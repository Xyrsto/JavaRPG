package dev.javarpg.runner.guard;

import dev.javarpg.core.contract.Profile;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Defense in depth only: a lexical denylist applied to code with comments and literals blanked.
 * The Docker container is the security boundary, not this class. The BASIC policy applies to
 * every profile in this feature.
 */
public final class SourceGuard {

    public static final int MAX_SOURCE_CHARS = 20000;

    private SourceGuard() {
    }

    public record Verdict(boolean allowed, String reason) {

        static Verdict accept() {
            return new Verdict(true, "");
        }

        static Verdict reject(String reason) {
            return new Verdict(false, reason);
        }
    }

    private record Rule(Pattern pattern, String reason) {
    }

    private static final List<Rule> RULES = List.of(
        rule("\\bSystem\\s*\\.\\s*(?:(?:exit|halt|in|load|loadLibrary|getenv|setIn|setOut|setErr|setSecurityManager)\\b|\\*)",
             "System.exit, System.in and other system calls are not allowed in this quest."),
        rule("\\bSystem\\s*::",
             "System method references are not allowed in this quest."),
        // The harness is compiled in the same package as player code; a player type with one of these
        // names would shadow the java.lang type (or the java package) the harness relies on.
        rule("\\b(?:class|interface|enum|record)\\s+(?:java|javax|jdk|Object|String|StringBuilder|Character|System|Throwable|Class|Integer|Math)\\b",
             "That name is reserved by Java. Pick a different name for your class."),
        rule("\\bimport\\s+static\\b",
             "Static imports are not allowed in this quest. Write the full name, like Math.max(...)."),
        rule("\\bRuntime\\b",
             "Runtime is not allowed here. Programs in this game cannot start other programs or control the machine."),
        rule("\\bProcess(Builder)?\\b",
             "Starting other programs is not allowed in this quest."),
        rule("\\bjava\\s*\\.\\s*(io|nio|net)\\b",
             "Files and the network are not available in this game."),
        rule("\\b(Socket|ServerSocket|HttpClient|InetAddress|URLConnection|DatagramSocket)\\b",
             "Network access is not allowed in this game."),
        rule("\\b(File|Files|Paths?|FileReader|FileWriter|FileInputStream|FileOutputStream|RandomAccessFile)\\b",
             "Reading and writing files is not allowed in this game."),
        rule("\\b(Thread|ForkJoin\\w*|CompletableFuture|Timer|Executor\\w*)\\b",
             "Threads and background tasks are not allowed in this quest."),
        rule("\\bforName\\b|\\bClassLoader\\b|\\bgetClassLoader\\b|\\bgetResource\\w*|\\bdefineClass\\b"
             + "|\\bgetDeclared\\w*|\\bget(Declared)?(Methods|Constructors|Fields)\\b"
             + "|\\bsetAccessible\\b|\\.\\s*getMethod\\s*\\(|\\.\\s*getConstructor\\s*\\("
             + "|\\bjava\\s*\\.\\s*lang\\s*\\.\\s*reflect\\b|\\binvoke\\b",
             "Looking inside Java's own classes (reflection) is not allowed in this quest."),
        rule("\\bMethodHandle\\w*|\\bVarHandle\\b|\\bProcessHandle\\b|\\bLambdaMetafactory\\b|\\bUnsafe\\b|\\bsun\\s*\\.|\\bjdk\\s*\\.|\\bjavax\\s*\\.\\s*tools\\b"
             + "|\\bSecurityManager\\b|\\bStackWalker\\b",
             "That low-level Java feature is not allowed in this quest.")
    );

    private static final Pattern UNICODE_ESCAPE = Pattern.compile("(?<!\\\\)(?:\\\\\\\\)*\\\\u");

    private static Rule rule(String regex, String reason) {
        return new Rule(Pattern.compile(regex), reason);
    }

    public static Verdict check(String source, Profile profile) {
        if (source == null || source.isBlank()) {
            return Verdict.reject("Write some code first.");
        }
        if (source.length() > MAX_SOURCE_CHARS) {
            return Verdict.reject("Your code is too long. Keep it under " + MAX_SOURCE_CHARS + " characters.");
        }
        // Checked on raw text: javac translates escapes before lexing, so a literal could hide code.
        // Only an odd number of backslashes before "u" starts an escape; an escaped backslash followed by a letter u is plain text.
        if (UNICODE_ESCAPE.matcher(source).find()) {
            return Verdict.reject("Escape codes like backslash-u are not allowed in this quest.");
        }
        // Raw text on purpose: adjacent literals could otherwise be joined into the protocol marker.
        if (source.contains("@@")) {
            return Verdict.reject("The text @@ is reserved for the game. Please remove it.");
        }
        String code = SourceScanner.blankLiteralsAndComments(source);
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(code).find()) {
                return Verdict.reject(rule.reason());
            }
        }
        return Verdict.accept();
    }
}
