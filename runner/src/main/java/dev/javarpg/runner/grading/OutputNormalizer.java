package dev.javarpg.runner.grading;

import java.util.ArrayList;
import java.util.List;

/** Removes whitespace noise so that comparisons ignore line endings and trailing spaces. */
public final class OutputNormalizer {

    private OutputNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String unified = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>();
        for (String line : unified.split("\n", -1)) {
            lines.add(stripTrailingSpaces(line));
        }
        int start = 0;
        while (start < lines.size() && lines.get(start).isEmpty()) {
            start++;
        }
        int end = lines.size();
        while (end > start && lines.get(end - 1).isEmpty()) {
            end--;
        }
        return String.join("\n", lines.subList(start, end));
    }

    private static String stripTrailingSpaces(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
            end--;
        }
        return line.substring(0, end);
    }
}
