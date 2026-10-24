package dev.javarpg.server.repository;

import dev.javarpg.server.exception.ContentLoadException;
import dev.javarpg.server.util.RegexLint;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Small assertion helpers shared by the content loaders (quest files, world file). Every failure is a
 * ContentLoadException that names the file and the offending key, so a bad content file stops start-up
 * with a message the author can act on.
 */
final class ContentChecks {

    private ContentChecks() {
    }

    static void check(String file, boolean ok, String key, String detail) {
        if (!ok) {
            throw new ContentLoadException(file + ": '" + key + "' " + detail);
        }
    }

    static void checkIn(String file, boolean ok, String key, String where, String detail) {
        if (!ok) {
            throw new ContentLoadException(file + ": '" + key + "' in " + where + " " + detail);
        }
    }

    static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    static void requireText(String file, String key, String value) {
        check(file, nonBlank(value), key, "must not be blank");
    }

    static List<String> requireLines(String file, String key, List<String> lines) {
        check(file, lines != null && !lines.isEmpty(), key, "must not be empty");
        for (String line : lines) {
            check(file, line != null, key, "must not contain null lines");
        }
        return lines;
    }

    /** where may be null for a top-level key. */
    static <E extends Enum<E>> E parseEnum(String file, String key, String where, String value, Class<E> type) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException e) {
            String detail = "must be one of " + Arrays.toString(type.getEnumConstants());
            if (where == null) {
                check(file, false, key, detail);
            } else {
                checkIn(file, false, key, where, detail);
            }
            throw new IllegalStateException("unreachable");
        }
    }

    /** Rejects invalid Java regexes and catastrophic-backtracking shapes (security review F-15). */
    static void requireSafeRegex(String file, String key, String where, String regex) {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException | NullPointerException e) {
            checkIn(file, false, key, where, "is not a valid Java regular expression");
        }
        checkIn(file, !RegexLint.hasNestedQuantifier(regex), key, where,
                "has nested quantifiers such as (a+)+ that can backtrack catastrophically; rewrite the pattern");
    }
}
