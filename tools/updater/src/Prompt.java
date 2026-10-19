import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Console answers with a time limit, shared by every question the server start asks. A timed-out question
 * leaves its read waiting; the next question takes that line instead of starting a second read, so two
 * readers never compete for one line. A hidden read (the API key) only starts when no read is waiting.
 */
final class Prompt {
    static final String END = "\u0000";
    private static final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private static boolean reading;
    private static BufferedReader in;

    private Prompt() { }

    /** The next line typed, or null when nothing came within the time; END when the input closed. */
    static String line(int seconds) throws InterruptedException {
        synchronized (Prompt.class) {
            if (!reading && lines.isEmpty()) {
                reading = true;
                Thread t = new Thread(Prompt::readOne, "console answer");
                t.setDaemon(true);
                t.start();
            }
        }
        return lines.poll(seconds, TimeUnit.SECONDS);
    }

    private static void readOne() {
        String s;
        try {
            Console c = System.console();
            if (c != null) {
                s = c.readLine();
            } else {
                if (in == null) in = new BufferedReader(new InputStreamReader(System.in));
                s = in.readLine();
            }
        } catch (IOException | RuntimeException e) {
            s = null;
        }
        synchronized (Prompt.class) {
            reading = false;
            lines.add(s == null ? END : s);
        }
    }

    /** True when a hidden read can start now (a console, and no earlier read still waiting for a line). */
    static synchronized boolean canReadSecret() {
        return System.console() != null && !reading && lines.isEmpty();
    }

    /** A line typed without echo; null without a console or when the input closed. Waits as long as it takes. */
    static String secret() {
        Console c = System.console();
        if (c == null || !canReadSecret()) return null;
        char[] typed = c.readPassword();
        if (typed == null) return null;
        String s = new String(typed);
        java.util.Arrays.fill(typed, ' ');
        return s;
    }
}
