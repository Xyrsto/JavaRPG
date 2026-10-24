package dev.javarpg.runner.guard;

/**
 * Single-pass lexer for Java source. It only separates code from comments and literals,
 * so the guard can inspect code alone. It is not a parser.
 */
public final class SourceScanner {

    private SourceScanner() {
    }

    /** Removes comments. String, char and text-block literals are kept as written. */
    public static String stripComments(String source) {
        return scan(source, false);
    }

    /**
     * Keeps every character position and line break, but replaces comment and literal
     * contents with spaces. Quote characters stay, so a string literal keeps its shape.
     */
    public static String blankLiteralsAndComments(String source) {
        return scan(source, true);
    }

    private static String scan(String src, boolean blank) {
        StringBuilder out = new StringBuilder(src.length());
        int n = src.length();
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    hidden(out, src.charAt(i), blank);
                    i++;
                }
            } else if (c == '/' && next == '*') {
                hidden(out, c, blank);
                hidden(out, next, blank);
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    hidden(out, src.charAt(i), blank);
                    i++;
                }
                if (i < n) {
                    hidden(out, '*', blank);
                    hidden(out, '/', blank);
                    i += 2;
                }
            } else if (src.startsWith("\"\"\"", i)) {
                i = textBlock(src, i, out, blank);
            } else if (c == '"' || c == '\'') {
                i = quoted(src, i, out, blank);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int textBlock(String src, int start, StringBuilder out, boolean blank) {
        int n = src.length();
        out.append("\"\"\"");
        int i = start + 3;
        while (i < n) {
            if (src.startsWith("\"\"\"", i)) {
                out.append("\"\"\"");
                return i + 3;
            }
            char c = src.charAt(i);
            if (c == '\\' && i + 1 < n) {
                content(out, c, blank);
                content(out, src.charAt(i + 1), blank);
                i += 2;
            } else {
                content(out, c, blank);
                i++;
            }
        }
        return n;
    }

    private static int quoted(String src, int start, StringBuilder out, boolean blank) {
        int n = src.length();
        char quote = src.charAt(start);
        out.append(quote);
        int i = start + 1;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\n') {
                // Unterminated literal: stop at the line end; the caller emits the newline.
                return i;
            }
            if (c == quote) {
                out.append(quote);
                return i + 1;
            }
            if (c == '\\' && i + 1 < n && src.charAt(i + 1) != '\n') {
                content(out, c, blank);
                content(out, src.charAt(i + 1), blank);
                i += 2;
            } else {
                content(out, c, blank);
                i++;
            }
        }
        return n;
    }

    /** Comment characters: dropped when stripping, blanked when blanking. Newlines always kept. */
    private static void hidden(StringBuilder out, char ch, boolean blank) {
        if (ch == '\n') {
            out.append('\n');
        } else if (blank) {
            out.append(' ');
        }
    }

    /** Literal characters: kept when stripping, blanked when blanking. Newlines always kept. */
    private static void content(StringBuilder out, char ch, boolean blank) {
        if (ch == '\n') {
            out.append('\n');
        } else if (blank) {
            out.append(' ');
        } else {
            out.append(ch);
        }
    }
}
