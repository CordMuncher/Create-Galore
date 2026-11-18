import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The latest GitHub release's pack info (Create-Galore-pack-info.txt), and the checks every value from it passes before
 * the updater uses it. The release names the exact commit its pack was built from, so players and servers install that
 * commit's pack (which can't change afterwards) rather than whatever is on the branch, and a push whose release build
 * failed never reaches them.
 */
final class Release {
    static final String REPO = "https://github.com/CordMuncher/Create-Galore";
    static final String INFO_URL = REPO + "/releases/latest/download/Create-Galore-pack-info.txt";
    static final String RAW = "https://raw.githubusercontent.com/CordMuncher/Create-Galore/";
    /** Only for releases made before the pack info named its commit. */
    static final String BRANCH_PACK_URL = RAW + "main-6fi2ef/pack/pack.toml";

    private static final Pattern VERSION = Pattern.compile("[0-9]{1,6}(\\.[0-9]{1,6}){1,3}");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern NEOFORGE = Pattern.compile("[0-9A-Za-z.+-]{1,40}");

    private Release() { }

    /** The latest release's pack info, or null when it can't be read. */
    static Map<String, String> info() {
        byte[] b = Net.get(INFO_URL, Net.TEXT_LIMIT);
        return b == null ? null : ServerUpdateCheck.parseKeyValues(new String(b, StandardCharsets.UTF_8));
    }

    /** The release's pack.toml at the commit it was built from, or null when the info doesn't name a valid commit. */
    static String packUrl(Map<String, String> info) {
        String commit = info == null ? null : info.get("commit");
        return validCommit(commit) ? RAW + commit + "/pack/pack.toml" : null;
    }

    /** A pack version like 0.6.92: digits and dots only, so it is safe in a file name and a URL. */
    static boolean validVersion(String v) {
        return v != null && VERSION.matcher(v).matches();
    }

    static boolean validCommit(String c) {
        return c != null && COMMIT.matcher(c).matches();
    }

    static boolean validSha256(String h) {
        return h != null && SHA256.matcher(h).matches();
    }

    /** A NeoForge version like 21.1.262 (it becomes a folder name under libraries/). */
    static boolean validNeoForge(String v) {
        return v != null && NEOFORGE.matcher(v).matches() && !v.contains("..");
    }
}
