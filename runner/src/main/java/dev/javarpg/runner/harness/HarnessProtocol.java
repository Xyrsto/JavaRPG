package dev.javarpg.runner.harness;

import java.util.regex.Pattern;

/**
 * Constants shared by the harness generator, the executors and the grader.
 * Protocol: the harness prints a per-run random nonce (32 lowercase hex characters) as the very first
 * stdout line, then one "@@JR-nonce|index|value" line per call. Player code cannot know the nonce
 * because System.out and System.err are replaced by a null sink while player methods run.
 */
public final class HarnessProtocol {

    /** File name the server gives the generated harness. Never player-controlled. */
    public static final String HARNESS_FILE = "JrHarness.java";

    /** Prefix of every result line, followed by the nonce. */
    public static final String RESULT_PREFIX = "@@JR";

    /** The nonce line: 32 lowercase hex characters. */
    public static final Pattern NONCE = Pattern.compile("[0-9a-f]{32}");

    /** Returned instead of javac output when the generated harness does not compile (F-10). */
    public static final String HARNESS_COMPILE_MESSAGE =
            "Your method does not match the signature the tests call.";

    /** Returned when javac itself ran too long. Keep identical to entrypoint.sh. */
    public static final String COMPILE_TIMEOUT_MESSAGE =
            "Your code took too long to compile. Try making it simpler.";

    private HarnessProtocol() {
    }
}
