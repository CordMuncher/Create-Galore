package dev.creategalore.fishing.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: every loaded fish file (Tide's format), so the journal, gadgets and fish lengths work on the
 * client. Sent on join and after {@code /reload}. A few hundred fish of JSON compress well, so the list travels as
 * one gzipped JSON array.
 */
public record FishDataSync(byte[] data) implements CustomPacketPayload {
    public static final Type<FishDataSync> TYPE = new Type<>(CreateGaloreFishing.id("fish_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FishDataSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE_ARRAY, FishDataSync::data, FishDataSync::new);

    public static FishDataSync of(Collection<FishData> fish) {
        JsonArray array = new JsonArray();
        for (FishData data : fish) {
            FishData.CODEC.encodeStart(JsonOps.INSTANCE, data)
                    .resultOrPartial(err -> CreateGaloreFishing.LOG.warn("Can't sync fish {}: {}", data.fish(), err))
                    .ifPresent(array::add);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Writer out = new OutputStreamWriter(new GZIPOutputStream(bytes), StandardCharsets.UTF_8)) {
            out.write(array.toString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new FishDataSync(bytes.toByteArray());
    }

    /** On the client (an integrated server has published the same data already). */
    public static void handle(FishDataSync payload) {
        if (FishingNet.isIntegratedServerHost()) return;
        JsonElement json;
        try (var in = new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(payload.data())),
                StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(in);
        } catch (IOException e) {
            CreateGaloreFishing.LOG.error("Bad fish data from the server", e);
            return;
        }
        List<FishData> fish = new ArrayList<>();
        for (JsonElement entry : json.getAsJsonArray()) {
            FishData.CODEC.parse(JsonOps.INSTANCE, entry)
                    .resultOrPartial(err -> CreateGaloreFishing.LOG.warn("Can't read synced fish: {}", err))
                    .ifPresent(fish::add);
        }
        FishData.publish(fish);
    }

    @Override
    public Type<FishDataSync> type() {
        return TYPE;
    }
}
