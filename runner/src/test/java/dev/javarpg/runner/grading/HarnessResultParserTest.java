package dev.javarpg.runner.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HarnessResultParserTest {

    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static final String PREFIX = "@@JR-" + NONCE + "|";
    private final HarnessResultParser parser = new HarnessResultParser();

    @Test
    void readsValuesAndExceptionsByIndex() {
        Map<Integer, HarnessResult> r = parser.parse(NONCE + "\n" + PREFIX + "0|5\n" + PREFIX + "1!EXC|boom\n");
        assertEquals(new HarnessResult(false, "5"), r.get(0));
        assertEquals(new HarnessResult(true, "boom"), r.get(1));
    }

    @Test
    void duplicateIndexMeansTamperingSoNothingIsAccepted() {
        assertTrue(parser.parse(NONCE + "\n" + PREFIX + "0|5\n" + PREFIX + "0|6\n").isEmpty());
    }

    @Test
    void badNonceOrWrongPrefixIsIgnored() {
        assertTrue(parser.parse("not-a-nonce\n" + PREFIX + "0|5\n").isEmpty());
        assertTrue(parser.parse(NONCE + "\n@@JR-ffffffffffffffffffffffffffffffff|0|5\n").isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }

    @Test
    void unescapeRestoresNewlinesAndBackslashes() {
        assertEquals("a\nb\r\\c", HarnessResultParser.unescape("a\\nb\\r\\\\c"));
        assertFalse(HarnessResultParser.unescape("tail\\").isEmpty());
    }
}
