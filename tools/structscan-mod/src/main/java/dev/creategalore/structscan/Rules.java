package dev.creategalore.structscan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * What-if rules, one per line: {@code <structure set pattern> <spacing scale> [frequency scale]}.
 * The pattern matches structure set ids (or the ids of the structures in the set) with * as a wildcard
 * ("dungeons_arise:*", "*:village_*", "*").
 * The first matching line wins. Lines starting with # are comments.
 */
final class Rules {
    record Rule(String pattern, Pattern regex, double spacing, double frequency) {}

    static List<Rule> read(Path file) throws IOException {
        List<Rule> out = new ArrayList<>();
        for (String raw : Files.readAllLines(file)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+");
            if (p.length < 2) throw new IOException("Bad rule line (need: pattern spacing [frequency]): " + raw);
            String regex = "\\Q" + p[0].replace("*", "\\E.*\\Q") + "\\E";
            out.add(new Rule(p[0], Pattern.compile(regex), Double.parseDouble(p[1]),
                    p.length > 2 ? Double.parseDouble(p[2]) : 1.0));
        }
        return out;
    }

    /** The first rule matching the set's id, or (for sets without a proper id) one of its structures' ids. */
    static Rule match(List<Rule> rules, String setId, List<String> structureIds) {
        for (Rule r : rules) {
            if (r.regex().matcher(setId).matches()) return r;
            for (String id : structureIds) if (r.regex().matcher(id).matches()) return r;
        }
        return null;
    }

    private Rules() {}
}
