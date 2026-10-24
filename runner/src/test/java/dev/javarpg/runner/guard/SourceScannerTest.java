package dev.javarpg.runner.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SourceScannerTest {

    private static int count(String s, char c) {
        int n = 0;
        for (char ch : s.toCharArray()) {
            if (ch == c) {
                n++;
            }
        }
        return n;
    }

    @Test
    void stripCommentsRemovesLineAndBlockComments() {
        String out = SourceScanner.stripComments("int a = 1; // System.exit(0)\nint b = /* Thread */ 2;");
        assertFalse(out.contains("System.exit"));
        assertFalse(out.contains("Thread"));
        assertTrue(out.contains("int a = 1;"));
        assertTrue(out.contains("int b ="));
        assertTrue(out.contains("2;"));
        assertTrue(out.contains("\n"));
    }

    @Test
    void stripCommentsKeepsStringLiterals() {
        String out = SourceScanner.stripComments("String s = \"System.exit\"; // gone");
        assertTrue(out.contains("\"System.exit\""));
        assertFalse(out.contains("gone"));
    }

    @Test
    void stripCommentsDoesNotTreatDivisionAsComment() {
        assertEquals("int z = 10 / 2;", SourceScanner.stripComments("int z = 10 / 2;"));
    }

    @Test
    void blankReplacesLiteralContentAndKeepsLength() {
        String src = "String s = \"System.exit\";";
        String out = SourceScanner.blankLiteralsAndComments(src);
        assertEquals(src.length(), out.length());
        assertFalse(out.contains("System"));
        assertTrue(out.startsWith("String s = \""));
        assertTrue(out.endsWith("\";"));
    }

    @Test
    void blankReplacesCommentsWithSpacesAndKeepsLines() {
        String src = "// Runtime here\nint x; /* Thread\nfoo */ int y;";
        String out = SourceScanner.blankLiteralsAndComments(src);
        assertEquals(src.length(), out.length());
        assertFalse(out.contains("Runtime"));
        assertFalse(out.contains("Thread"));
        assertFalse(out.contains("foo"));
        assertEquals(2, count(out, '\n'));
        assertTrue(out.contains("int x;"));
        assertTrue(out.contains("int y;"));
    }

    @Test
    void escapedQuoteInsideStringDoesNotEndTheString() {
        String src = "String s = \"a\\\"System.exit(0)\\\"b\";";
        String out = SourceScanner.blankLiteralsAndComments(src);
        assertEquals(src.length(), out.length());
        assertFalse(out.contains("System"));
        assertTrue(out.endsWith("\";"));
    }

    @Test
    void charLiteralQuoteDoesNotHideCode() {
        String out = SourceScanner.blankLiteralsAndComments("char q = '\"'; System.exit(0);");
        assertTrue(out.contains("System.exit(0)"));
    }

    @Test
    void textBlockContentIsBlankedAndCodeAfterItStillScanned() {
        String src = "String t = \"\"\"\n  System.exit(0)\n  \"\"\"; System.exit(1);";
        String out = SourceScanner.blankLiteralsAndComments(src);
        assertFalse(out.contains("System.exit(0)"));
        assertTrue(out.contains("System.exit(1)"));
    }
}
