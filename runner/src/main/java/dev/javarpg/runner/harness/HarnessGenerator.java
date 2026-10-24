package dev.javarpg.runner.harness;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds the JrHarness source that calls the player's Solution methods. The harness holds only
 * call expressions (inputs), never expected values. It prints a random nonce as its first stdout
 * line, silences System.out/err while player methods run, and prints each result to the saved stream
 * as "@@JR-nonce|index|escaped value" (or "index!EXC" with the exception). See HarnessProtocol.
 */
public final class HarnessGenerator {

    private static final Pattern CALL = Pattern.compile("Solution\\.[A-Za-z_][A-Za-z0-9_]*\\(.*\\)");

    private static final String SHOW_METHOD = String.join("\n",
        "    static String show(Object o) {",
        "        if (o == null) {",
        "            return \"null\";",
        "        }",
        "        String text;",
        "        if (o.getClass().isArray()) {",
        "            text = java.util.Arrays.deepToString(new Object[] { o });",
        "            text = text.substring(1, text.length() - 1);",
        "        } else {",
        "            text = String.valueOf(o);",
        "        }",
        "        char bs = (char) 92;",
        "        StringBuilder sb = new StringBuilder();",
        "        for (int i = 0; i < text.length(); i++) {",
        "            char c = text.charAt(i);",
        "            if (c == bs) {",
        "                sb.append(bs).append(bs);",
        "            } else if (c == (char) 10) {",
        "                sb.append(bs).append('n');",
        "            } else if (c == (char) 13) {",
        "                sb.append(bs).append('r');",
        "            } else {",
        "                sb.append(c);",
        "            }",
        "        }",
        "        return sb.toString();",
        "    }");

    private HarnessGenerator() {
    }

    public static String generate(List<String> calls) {
        if (calls == null) {
            throw new IllegalArgumentException("calls must not be null");
        }
        StringBuilder src = new StringBuilder();
        src.append("public class JrHarness {\n");
        src.append("    public static void main(String[] args) {\n");
        // Saved before any player class is initialised (Solution initialises lazily at its first call).
        src.append("        java.io.PrintStream out = System.out;\n");
        src.append("        byte[] raw = new byte[16];\n");
        src.append("        new java.security.SecureRandom().nextBytes(raw);\n");
        src.append("        StringBuilder hex = new StringBuilder();\n");
        src.append("        for (byte b : raw) {\n");
        src.append("            hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));\n");
        src.append("        }\n");
        src.append("        String nonce = hex.toString();\n");
        src.append("        out.println(nonce);\n");
        src.append("        out.flush();\n");
        src.append("        java.io.PrintStream sink = new java.io.PrintStream(java.io.OutputStream.nullOutputStream());\n");
        src.append("        System.setOut(sink);\n");
        src.append("        System.setErr(sink);\n");
        for (int i = 0; i < calls.size(); i++) {
            String call = checkCall(calls.get(i));
            src.append("        try { String v = show(").append(call).append("); out.println(\"@@JR-\" + nonce + \"|")
               .append(i).append("|\" + v); }\n");
            src.append("        catch (Throwable t) { out.println(\"@@JR-\" + nonce + \"|").append(i)
               .append("!EXC|\" + show(t.getClass().getName() + \": \" + t.getMessage())); }\n");
            src.append("        out.flush();\n");
        }
        src.append("    }\n");
        src.append(SHOW_METHOD).append("\n");
        src.append("}\n");
        return src.toString();
    }

    static String checkCall(String call) {
        if (call == null || !CALL.matcher(call).matches() || call.contains(";")
                || call.contains("//") || call.contains("/*")
                || call.indexOf('\n') >= 0 || call.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Unsafe or malformed call: " + call);
        }
        return call;
    }
}
