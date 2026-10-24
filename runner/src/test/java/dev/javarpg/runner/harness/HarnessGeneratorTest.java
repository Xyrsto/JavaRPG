package dev.javarpg.runner.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HarnessGeneratorTest {

    private static int countOccurrences(String text, String part) {
        int n = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(part, from);
            if (at < 0) {
                return n;
            }
            n++;
            from = at + part.length();
        }
    }

    @Test
    void containsHarnessClassIndexedCallsAndProtocolMarker() {
        String src = HarnessGenerator.generate(List.of("Solution.add(2, 3)", "Solution.sub(5, 1)"));
        assertTrue(src.contains("class JrHarness"));
        assertEquals(2, countOccurrences(src, "catch (Throwable"));
        assertTrue(src.contains("\"@@JR-\" + nonce + \"|0|\""));
        assertTrue(src.contains("\"@@JR-\" + nonce + \"|1|\""));
        assertTrue(src.contains("\"@@JR-\" + nonce + \"|1!EXC|\""));
        assertTrue(src.contains("show(Solution.add(2, 3))"));
        assertTrue(src.contains("show(Solution.sub(5, 1))"));
    }

    @Test
    void nonceIsRandomPrintedFirstAndStreamsAreSilencedBeforePlayerCode() {
        String src = HarnessGenerator.generate(List.of("Solution.add(2, 3)"));
        int secure = src.indexOf("new java.security.SecureRandom()");
        int printNonce = src.indexOf("out.println(nonce);");
        int setOut = src.indexOf("java.lang.System.setOut(sink);");
        int setErr = src.indexOf("java.lang.System.setErr(sink);");
        int firstCall = src.indexOf("show(Solution.add");
        assertTrue(secure > 0 && secure < printNonce);
        assertTrue(printNonce < setOut && setOut < firstCall);
        assertTrue(setErr < firstCall);
        assertTrue(src.contains("java.io.PrintStream out = java.lang.System.out;"));
        assertTrue(src.indexOf("java.io.PrintStream out = java.lang.System.out;") < secure);
        assertFalse(src.contains("@@JR|"));
        assertFalse(java.util.regex.Pattern.compile("[0-9a-f]{32}").matcher(src).find());
        assertEquals(src, HarnessGenerator.generate(List.of("Solution.add(2, 3)")));
    }

    @Test
    void emptyCallListGivesValidMain() {
        String src = HarnessGenerator.generate(List.of());
        assertTrue(src.contains("public static void main"));
        assertTrue(src.contains("class JrHarness"));
        assertFalse(src.contains("catch (Throwable"));
    }

    @Test
    void showHandlesNullAndArrays() {
        String src = HarnessGenerator.generate(List.of("Solution.x()"));
        assertTrue(src.contains("Arrays.deepToString"));
        assertTrue(src.contains("if (o == null)"));
    }

    @Test
    void rejectsCallsThatWouldInjectCode() {
        assertThrows(IllegalArgumentException.class, () -> HarnessGenerator.generate(List.of("Solution.add(1, 2); System.exit(0)")));
        assertThrows(IllegalArgumentException.class, () -> HarnessGenerator.generate(List.of("System.exit(0)")));
        assertThrows(IllegalArgumentException.class, () -> HarnessGenerator.generate(List.of("Solution.a(1) // x")));
    }
}
