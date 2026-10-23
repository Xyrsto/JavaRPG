package dev.javarpg.runner.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OutputNormalizerTest {

    @Test
    void convertsCrlfAndCrToLf() {
        assertEquals("a\nb", OutputNormalizer.normalize("a\r\nb"));
        assertEquals("a\nb", OutputNormalizer.normalize("a\rb"));
    }

    @Test
    void removesTrailingSpacesAndTabsPerLine() {
        assertEquals("a\n  b", OutputNormalizer.normalize("a  \n  b\t \t"));
    }

    @Test
    void stripsLeadingAndTrailingBlankLinesButKeepsInnerOnes() {
        assertEquals("a\n\nb", OutputNormalizer.normalize("\n\na\n\nb\n\n\n"));
    }

    @Test
    void nullBecomesEmpty() {
        assertEquals("", OutputNormalizer.normalize(null));
    }

    @Test
    void blankOnlyTextBecomesEmpty() {
        assertEquals("", OutputNormalizer.normalize("\n  \n"));
    }
}
