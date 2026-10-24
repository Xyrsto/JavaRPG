package dev.javarpg.runner.grading;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BoundedRegexTest {

    @Test
    void findsAnOrdinaryMatch() {
        assertTrue(BoundedRegex.finds("\\bint\\s+x", "int x = 1;"));
        assertFalse(BoundedRegex.finds("\\bint\\s+y", "int x = 1;"));
    }

    @Test
    void badPatternOrNullInputCountsAsNotFound() {
        assertFalse(BoundedRegex.finds("(", "text"));
        assertFalse(BoundedRegex.finds(null, "text"));
        assertFalse(BoundedRegex.finds("a", null));
    }

    @Test
    void catastrophicPatternIsStoppedByTheStepLimit() {
        String evil = "(a+)+$";
        String input = "a".repeat(40) + "!";
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertFalse(BoundedRegex.finds(evil, input)));
    }

    @Test
    void smallStepLimitStopsEvenSimpleScans() {
        assertFalse(BoundedRegex.finds("z", "a".repeat(1000) + "z", 10L));
    }
}
