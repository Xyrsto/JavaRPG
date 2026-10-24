package dev.javarpg.server.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegexLintTest {

    @Test
    void rejectsTheClassicCatastrophicShapes() {
        for (String regex : new String[] {"(a+)+", "(a*)*", "(.*)*", "(\\s*\\w+)*", "((a+))+", "(a+){2,}",
                "(?:a+)+b", "(a|b+)*", "([ab]+)+", "(a{2,})+"}) {
            assertTrue(RegexLint.hasNestedQuantifier(regex), regex);
        }
    }

    @Test
    void acceptsOrdinaryPatterns() {
        for (String regex : new String[] {"int\\s+x", "(int|double)\\s+x+", "a+b*", "(ab)?c", "(a?)+", "(a){1}",
                "[(a+)+]", "\\(a+\\)+", "for\\s*\\(", "(?i)hello", "x{0,1}"}) {
            assertFalse(RegexLint.hasNestedQuantifier(regex), regex);
        }
    }

    @Test
    void survivesTruncatedPatterns() {
        for (String regex : new String[] {"", "(", ")", "[", "\\", "(?", "a{", "(a+"}) {
            RegexLint.hasNestedQuantifier(regex);
        }
    }
}
