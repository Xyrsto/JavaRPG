package dev.javarpg.server.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MethodCallWhitelistTest {

    @Test
    void allowsLiteralArguments() {
        for (String call : new String[] {"Solution.f()", "Solution.add(2, -3)", "Solution.s(\"Mira\", 'c', true, 2.5)",
                "Solution.sum(new int[]{1, 2, 3})", "Solution.n(null)", "Solution.big(10L)"}) {
            assertTrue(MethodCallWhitelist.isAllowed(call), call);
        }
    }

    @Test
    void rejectsAnythingThatCouldBeCode() {
        for (String call : new String[] {null, "", "Solution.a(1)) + evil((2)", "Solution.a(System.exit(0))",
                "Solution.a(1) // x", "Solution.a(/*c*/1)", "Solution.a(b)", "Solution.a(1)(2)", "Other.a(1)",
                "Solution.a(1); Runtime.exit()", "Solution.a(\"x;y\")"}) {
            assertFalse(MethodCallWhitelist.isAllowed(call), String.valueOf(call));
        }
    }
}
