package dev.javarpg.runner.grading;

import dev.javarpg.runner.harness.HarnessProtocol;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the harness protocol lines out of a run's normalized stdout. */
public final class HarnessResultParser {

    /** Body of a result line after the "@@JR-nonce|" prefix. */
    private static final Pattern RESULT_BODY = Pattern.compile("(\\d{1,6})(!EXC)?\\|(.*)", Pattern.DOTALL);

    /**
     * The first line is the run's nonce; only lines that start with "@@JR-nonce|" count. A duplicate
     * index means tampering, so nothing is accepted (the caller then fails every call test).
     */
    Map<Integer, HarnessResult> parse(String text) {
        Map<Integer, HarnessResult> map = new HashMap<>();
        if (text == null || text.isEmpty()) {
            return map;
        }
        String[] lines = text.split("\n", -1);
        String nonce = lines[0].trim();
        if (!HarnessProtocol.NONCE.matcher(nonce).matches()) {
            return map;
        }
        String prefix = HarnessProtocol.RESULT_PREFIX + "-" + nonce + "|";
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (!line.startsWith(prefix)) {
                continue;
            }
            Matcher m = RESULT_BODY.matcher(line.substring(prefix.length()));
            if (!m.matches()) {
                continue;
            }
            int index = Integer.parseInt(m.group(1));
            if (map.containsKey(index)) {
                return new HashMap<>();
            }
            map.put(index, new HarnessResult(m.group(2) != null, unescape(m.group(3))));
        }
        return map;
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == 'n') {
                    sb.append('\n');
                    i++;
                    continue;
                }
                if (n == 'r') {
                    sb.append('\r');
                    i++;
                    continue;
                }
                if (n == '\\') {
                    sb.append('\\');
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
