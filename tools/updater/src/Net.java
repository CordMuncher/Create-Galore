import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Downloads for the updater, over HTTPS only. A redirect is followed only to another https:// address, so nothing the
 * updater reads (the pack, the release info, the Server Setup app) can be swapped on the way by someone on the network.
 * Bodies are capped, so a broken or hostile answer can't fill the memory.
 */
final class Net {
    /** Release info, pack.toml and other small text files. */
    static final int TEXT_LIMIT = 1 << 20;
    /** The Server Setup app. */
    static final int APP_LIMIT = 256 << 20;
    private static final int MAX_HOPS = 5;

    /** Why the last {@link #get} returned null, for the message the user sees. */
    static volatile String lastProblem = "";

    private Net() { }

    /** True when the text is a web address (http or https), as opposed to a local file path. */
    static boolean isUrl(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        return l.startsWith("http://") || l.startsWith("https://");
    }

    /** True for a well-formed https:// address with a host. */
    static boolean isHttps(String url) {
        try {
            URI u = new URI(url);
            return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && !u.getHost().isEmpty();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * True for an API base address the Anthropic key may be sent to: https://, or plain http:// only to this computer
     * (a local proxy). Anything else would send the key unencrypted.
     */
    static boolean keySafe(String base) {
        if (isHttps(base)) return true;
        try {
            URI u = new URI(base);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            return "http".equalsIgnoreCase(u.getScheme())
                && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1"));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** The body of an https:// address, or null when it can't be read (offline, not 200, too big, insecure redirect). */
    static byte[] get(String url, int limit) {
        lastProblem = "";
        try {
            for (int hops = 0; hops <= MAX_HOPS; hops++) {
                if (!isHttps(url)) {
                    lastProblem = "refused a non-https address: " + url;
                    return null;
                }
                HttpURLConnection c = (HttpURLConnection) new URI(url).toURL().openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(60000);
                c.setRequestProperty("User-Agent", "create-galore-updater");
                c.setInstanceFollowRedirects(false);
                try {
                    int status = c.getResponseCode();
                    if (status / 100 == 3) {
                        String location = c.getHeaderField("Location");
                        if (location == null) {
                            lastProblem = "redirect without an address";
                            return null;
                        }
                        url = new URI(url).resolve(location).toString();
                        continue;
                    }
                    if (status != 200) {
                        lastProblem = "HTTP " + status;
                        return null;
                    }
                    if (c.getContentLengthLong() > limit) {
                        lastProblem = "answer too large";
                        return null;
                    }
                    try (InputStream in = c.getInputStream()) {
                        ByteArrayOutputStream b = new ByteArrayOutputStream();
                        byte[] buf = new byte[1 << 16];
                        for (int n; (n = in.read(buf)) > 0; ) {
                            if (b.size() + n > limit) {
                                lastProblem = "answer too large";
                                return null;
                            }
                            b.write(buf, 0, n);
                        }
                        return b.toByteArray();
                    }
                } finally {
                    c.disconnect();
                }
            }
            lastProblem = "too many redirects";
        } catch (IOException | URISyntaxException | RuntimeException e) {
            lastProblem = e.toString();
        }
        return null;
    }
}
