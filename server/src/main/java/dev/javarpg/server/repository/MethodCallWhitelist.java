package dev.javarpg.server.repository;

import java.util.regex.Pattern;

/**
 * Strict whitelist for a METHOD_RETURN call (security review F-15): Solution.method( args ) where each
 * arg is a number, string or char literal, true/false/null, or new T[]{literals}. No nested calls, no
 * ';', no comments, no ")(" sequence. The call text ends up in generated Java source, so it must stay
 * data, never code.
 */
final class MethodCallWhitelist {

    private static final String SCALAR = "(?:-?[0-9]+(?:\\.[0-9]+)?[LlFfDd]?"
            + "|\"(?:[^\"\\\\;/()]|\\\\[\"\\\\nt])*\""
            + "|'(?:[^'\\\\;/()]|\\\\['\\\\nt])'"
            + "|true|false|null)";
    private static final String ARRAY = "new[ ]+[A-Za-z_][A-Za-z0-9_]*[ ]*\\[[ ]*\\][ ]*\\{[ ]*(?:" + SCALAR
            + "(?:[ ]*,[ ]*" + SCALAR + ")*)?[ ]*\\}";
    private static final String ARG = "(?:" + SCALAR + "|" + ARRAY + ")";
    private static final Pattern METHOD_CALL = Pattern.compile("^Solution\\.[A-Za-z_][A-Za-z0-9_]*\\([ ]*(?:" + ARG
            + "(?:[ ]*,[ ]*" + ARG + ")*)?[ ]*\\)$");

    private MethodCallWhitelist() {
    }

    /** True only for a single Solution.method(literal, ...) call; null is never allowed. */
    static boolean isAllowed(String call) {
        return call != null && METHOD_CALL.matcher(call).matches()
                && !call.contains(";") && !call.contains("//") && !call.contains("/*")
                && !call.matches("(?s).*\\)\\s*\\(.*");
    }
}
