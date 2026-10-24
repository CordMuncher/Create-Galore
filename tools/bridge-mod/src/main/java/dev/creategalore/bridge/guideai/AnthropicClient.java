package dev.creategalore.bridge.guideai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calls the Claude Messages API over plain HTTPS with the JDK client. The official Java SDK would drag Kotlin,
 * OkHttp and Jackson into a mod that other mods share a classpath with (and this mod is built without Gradle, so
 * nothing could relocate them); the API is one JSON POST, so the JDK and Minecraft's Gson are enough.
 *
 * The key lives only in this object and only travels in the x-api-key header of requests to the configured API
 * address. Errors are reported by status and error type, never with request headers.
 */
final class AnthropicClient {
    static final String VERSION = "2023-06-01";
    /** Lets a refused request retry on the model Anthropic recommends for that refusal category. */
    // Thinking binding controls let a request drop a thinking block whose conversation no longer matches instead of
    // being refused (GuideAi sets prefix_mismatch_behavior).
    static final String BETAS = "server-side-fallback-2026-07-01,thinking-binding-controls-2026-08-01";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String baseUrl;
    private final String key;

    AnthropicClient(String baseUrl, String key) {
        this.baseUrl = baseUrl;
        this.key = key;
    }

    /** A failed call, already worded for the player. */
    static final class ApiException extends Exception {
        final int status;
        final String type;

        ApiException(int status, String type, String playerMessage) {
            super(playerMessage);
            this.status = status;
            this.type = type;
        }
    }

    private HttpRequest request(JsonObject body) {
        return HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                .timeout(Duration.ofMinutes(5))
                .header("content-type", "application/json")
                .header("anthropic-version", VERSION)
                .header("anthropic-beta", BETAS)
                .header("x-api-key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    private static ApiException failure(int status, String responseBody) {
        String type = "unknown", message = "";
        try {
            JsonElement parsed = JsonParser.parseString(responseBody);
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("error") && parsed.getAsJsonObject().get("error").isJsonObject()) {
                JsonObject error = parsed.getAsJsonObject().getAsJsonObject("error");
                if (error.has("type")) type = error.get("type").getAsString();
                if (error.has("message")) message = error.get("message").getAsString();
            }
        } catch (RuntimeException ignored) {
            // not JSON; the status says enough
        }
        return new ApiException(status, type + (message.isEmpty() ? "" : ": " + message), playerMessage(status, message));
    }

    /** What a streamed answer reports while it is being written. */
    interface Listener {
        /** A new content block began (text, thinking, tool_use, server_tool_use and so on). */
        void blockStart(JsonObject block);

        /** More answer text. */
        void text(String delta);
    }

    /**
     * One Messages API call, streamed: text arrives through the listener as it is written, and the
     * finished message comes back in the shape the non-streamed API returns, every block rebuilt verbatim (thinking
     * signatures, tool inputs, citations) so it can go back into the conversation unchanged.
     */
    JsonObject stream(JsonObject body, Listener listener) throws ApiException, InterruptedException {
        JsonObject streamed = body.deepCopy();
        streamed.addProperty("stream", true);
        HttpRequest request = request(streamed);
        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new ApiException(0, "connection", "The book couldn't reach its library (no connection to Anthropic). Try again in a moment.");
        }
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200)
                throw failure(response.statusCode(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            JsonObject message = null;
            Map<Integer, JsonObject> blocks = new TreeMap<>();
            Map<Integer, StringBuilder> inputs = new TreeMap<>();
            StringBuilder data = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) {
                if (Thread.interrupted()) throw new InterruptedException();
                if (line.startsWith("data:")) {
                    data.append(line.substring(5).strip());
                    continue;
                }
                if (!line.isEmpty() || data.isEmpty()) continue;
                JsonObject event = JsonParser.parseString(data.toString()).getAsJsonObject();
                data.setLength(0);
                switch (GuideText.string(event, "type")) {
                    case "message_start" -> message = event.getAsJsonObject("message");
                    case "content_block_start" -> {
                        int index = event.get("index").getAsInt();
                        JsonObject block = event.getAsJsonObject("content_block").deepCopy();
                        String type = GuideText.string(block, "type");
                        if (type.equals("tool_use") || type.equals("server_tool_use")) inputs.put(index, new StringBuilder());
                        blocks.put(index, block);
                        listener.blockStart(block);
                    }
                    case "content_block_delta" -> {
                        int index = event.get("index").getAsInt();
                        JsonObject block = blocks.get(index);
                        JsonObject delta = event.getAsJsonObject("delta");
                        if (block == null) break;
                        switch (GuideText.string(delta, "type")) {
                            case "text_delta" -> {
                                String t = GuideText.string(delta, "text");
                                block.addProperty("text", GuideText.string(block, "text") + t);
                                listener.text(t);
                            }
                            case "thinking_delta" -> block.addProperty("thinking", GuideText.string(block, "thinking") + GuideText.string(delta, "thinking"));
                            case "signature_delta" -> block.addProperty("signature", GuideText.string(delta, "signature"));
                            case "input_json_delta" -> inputs.computeIfAbsent(index, k -> new StringBuilder()).append(GuideText.string(delta, "partial_json"));
                            case "citations_delta" -> {
                                if (!block.has("citations") || !block.get("citations").isJsonArray()) block.add("citations", new com.google.gson.JsonArray());
                                block.getAsJsonArray("citations").add(delta.get("citation"));
                            }
                            default -> {
                            }
                        }
                    }
                    case "content_block_stop" -> {
                        int index = event.get("index").getAsInt();
                        StringBuilder input = inputs.remove(index);
                        if (input != null && blocks.containsKey(index))
                            blocks.get(index).add("input", input.isEmpty() ? new JsonObject() : JsonParser.parseString(input.toString()));
                    }
                    case "message_delta" -> {
                        if (message == null) break;
                        JsonObject delta = event.getAsJsonObject("delta");
                        for (String k : delta.keySet()) message.add(k, delta.get(k));
                        if (event.has("usage") && event.get("usage").isJsonObject()) {
                            if (!message.has("usage") || !message.get("usage").isJsonObject()) message.add("usage", new JsonObject());
                            JsonObject usage = message.getAsJsonObject("usage");
                            JsonObject more = event.getAsJsonObject("usage");
                            for (String k : more.keySet()) usage.add(k, more.get(k));
                        }
                    }
                    case "error" -> {
                        JsonObject wrapped = new JsonObject();
                        wrapped.add("error", event.get("error"));
                        String type = event.has("error") ? GuideText.string(event.getAsJsonObject("error"), "type") : "";
                        throw failure(type.contains("overloaded") ? 529 : 500, wrapped.toString());
                    }
                    default -> {
                    }
                }
            }
            if (message == null || !message.has("stop_reason") || message.get("stop_reason").isJsonNull())
                throw new ApiException(0, "stream ended early", "The book lost its connection mid-sentence. Try again.");
            com.google.gson.JsonArray content = new com.google.gson.JsonArray();
            blocks.values().forEach(content::add);
            message.add("content", content);
            return message;
        } catch (IOException | RuntimeException e) {
            throw new ApiException(0, "stream " + e.getClass().getSimpleName(), "The book lost its connection mid-sentence. Try again.");
        }
    }

    static String playerMessage(int status, String apiMessage) {
        String m = apiMessage.toLowerCase(Locale.ROOT);
        if (status == 401 || status == 403)
            return "The book's key was refused. The server owner needs to save a new API key (start the server and press K).";
        if (m.contains("credit balance") || m.contains("usage limit") || m.contains("spend limit") || status == 402)
            return "The book has run out of ink for this month (the API credit or spend limit is used up). The written guide still works.";
        if (status == 429 || status == 529 || status >= 500)
            return "The book is busy right now. Try again in a minute.";
        return "The book couldn't answer that (error " + status + "). The server log has the details.";
    }
}
