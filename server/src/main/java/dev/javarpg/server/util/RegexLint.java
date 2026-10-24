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
        return new Scan(regex).findNestedQuantifier();
    }

    /** Whether the group being scanned has contained a repetition so far. */
    private static final class Group {
        boolean hasRepetition;
    }

    /** One left-to-right pass over the pattern, tracking the stack of open groups. */
    private static final class Scan {

        private final String regex;
        private final int length;
        private final Deque<Group> enclosing = new ArrayDeque<>();
        private Group current = new Group();
        private int pos;

        Scan(String regex) {
            this.regex = regex;
            this.length = regex.length();
        }

        boolean findNestedQuantifier() {
            while (pos < length) {
                char c = regex.charAt(pos);
                if (c == '\\') {
                    pos += 2;
                    skipAtomQuantifier();
                } else if (c == '[') {
                    pos = skipClass(pos);
                    skipAtomQuantifier();
                } else if (c == '(') {
                    openGroup();
                } else if (c == ')') {
                    if (closeGroup()) {
                        return true;
                    }
                } else {
                    pos++;
                    skipAtomQuantifier();
                }
            }
            return false;
        }

        private void openGroup() {
            enclosing.push(current);
            current = new Group();
            pos++;
            if (pos < length && regex.charAt(pos) == '?') {
                pos++;
                while (pos < length && ":=!>)".indexOf(regex.charAt(pos)) < 0) {
                    pos++;
                }
                if (pos < length && regex.charAt(pos) != ')') {
                    pos++;
                }
            }
        }

        /** Closes the group; returns true when a repeated group contains a repetition. */
        private boolean closeGroup() {
            pos++;
            boolean innerRepeats = current.hasRepetition;
            current = enclosing.isEmpty() ? new Group() : enclosing.pop();
            int quantifierEnd = quantifierEnd(pos);
            if (quantifierEnd > pos) {
                boolean repeating = isRepeating(regex.substring(pos, quantifierEnd));
                if (innerRepeats && repeating) {
                    return true;
                }
                if (repeating) {
                    innerRepeats = true;
                }
                pos = skipLazyOrPossessive(quantifierEnd);
            }
            if (innerRepeats) {
                current.hasRepetition = true;
            }
            return false;
        }

        /** Consumes an optional quantifier after an atom and notes whether it repeats. */
        private void skipAtomQuantifier() {
            int quantifierEnd = quantifierEnd(pos);
            if (quantifierEnd > pos) {
                if (isRepeating(regex.substring(pos, quantifierEnd))) {
                    current.hasRepetition = true;
                }
                pos = skipLazyOrPossessive(quantifierEnd);
            }
        }

        private int skipClass(int start) {
            int i = start + 1;
            if (i < length && regex.charAt(i) == '^') {
                i++;
            }
            if (i < length && regex.charAt(i) == ']') {
                i++;
            }
            int depth = 1;
            while (i < length && depth > 0) {
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
        private int quantifierEnd(int i) {
            if (i >= length) {
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

        private int skipLazyOrPossessive(int i) {
            if (i < length && (regex.charAt(i) == '?' || regex.charAt(i) == '+')) {
                return i + 1;
            }
            return i;
        }
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
}
