package dev.javarpg.core.drill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DrillFailTest {
    @Test
    void deliberatelyFailing() {
        assertEquals(1, 2, "CI drill: this test fails on purpose");
    }
}
// second commit pushed while the first run is in progress (drill D5)
