package dev.javarpg.server.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Rejects regular expressions with nested quantifiers such as (a+)+, (a*)*, (.*)* or (\s*\w+)*, the
 * classic catastrophic-backtracking shapes (security review F-15). Quest patterns run against
 * player-controlled text, so a pattern like this would be a player-triggerable DoS.
 * The check is a conservative heuristic: it may reject some safe patterns, never accepts these shapes.
 */
public final class RegexLint {

    private RegexLint() {
    }

    /** Returns true when the pattern has a repeated group that itself contains a repetition. */
    public static boolean hasNestedQuantifier(String regex) {
        Deque<boolean[]> groups = new ArrayDeque<>();
        boolean[] current = new boolean[] {false}; // [0] = contains a repetition (top level scratch)
        int i = 0;
        int n = regex.length();
        while (i < n) {
            char c = regex.charAt(i);
            if (c == '\\') {
                i += 2;
                i = afterAtomQuantifier(regex, i, current);
                continue;
            }
            if (c == '[') {
                i = skipClass(regex, i);
                i = afterAtomQuantifier(regex, i, current);
                continue;
            }
            if (c == '(') {
                groups.push(current);
                current = new boolean[] {false};
                i++;
                if (i < n && regex.charAt(i) == '?') {
                    i++;
                    while (i < n && ":=!>)".indexOf(regex.charAt(i)) < 0) {
                        i++;
                    }
                    if (i < n && regex.charAt(i) != ')') {
                        i++;
                    }
                }
                continue;
            }
            if (c == ')') {
                i++;
                boolean innerHasRepeat = current[0];
                current = groups.isEmpty() ? new boolean[] {false} : groups.pop();
                int qEnd = quantifierEnd(regex, i);
                if (qEnd > i) {
                    if (innerHasRepeat && isRepeating(regex.substring(i, qEnd))) {
                        return true;
                    }
                    if (isRepeating(regex.substring(i, qEnd))) {
                        innerHasRepeat = true;
                    }
                    i = skipLazyOrPossessive(regex, qEnd);
                }
                if (innerHasRepeat) {
                    current[0] = true;
                }
                continue;
            }
            i++;
            i = afterAtomQuantifier(regex, i, current);
        }
        return false;
    }

    private static int afterAtomQuantifier(String regex, int i, boolean[] current) {
        int qEnd = quantifierEnd(regex, i);
        if (qEnd > i) {
            if (isRepeating(regex.substring(i, qEnd))) {
                current[0] = true;
            }
            return skipLazyOrPossessive(regex, qEnd);
        }
        return i;
    }

    private static int skipClass(String regex, int start) {
        int i = start + 1;
        int n = regex.length();
        if (i < n && regex.charAt(i) == '^') {
            i++;
        }
        if (i < n && regex.charAt(i) == ']') {
            i++;
        }
        int depth = 1;
        while (i < n && depth > 0) {
            char c = regex.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            }
            i++;
        }
        return i;
    }

    /** Index just after a quantifier starting at i, or i when there is none. */
    private static int quantifierEnd(String regex, int i) {
        if (i >= regex.length()) {
            return i;
        }
        char c = regex.charAt(i);
        if (c == '*' || c == '+' || c == '?') {
            return i + 1;
        }
        if (c == '{') {
            int close = regex.indexOf('}', i);
            if (close > i && regex.substring(i + 1, close).matches("[0-9]+(,[0-9]*)?")) {
                return close + 1;
            }
        }
        return i;
    }

    /** True for * + and {n,} {n,m} with m greater than 1; false for ? and {0} {1} {0,1}. */
    private static boolean isRepeating(String q) {
        if (q.equals("*") || q.equals("+")) {
            return true;
        }
        if (q.equals("?")) {
            return false;
        }
        String body = q.substring(1, q.length() - 1);
        int comma = body.indexOf(',');
        if (comma < 0) {
            return Integer.parseInt(body) > 1;
        }
        String max = body.substring(comma + 1);
        return max.isEmpty() || Integer.parseInt(max) > 1;
    }

    private static int skipLazyOrPossessive(String regex, int i) {
        if (i < regex.length() && (regex.charAt(i) == '?' || regex.charAt(i) == '+')) {
            return i + 1;
        }
        return i;
    }
}
