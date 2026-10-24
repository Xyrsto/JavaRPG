package dev.javarpg.runner.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorTranslatorTest {

    private static final String SEMICOLON = "Java statements end with a semicolon (;). Check the end of the line the error points to.";
    private static final String UNKNOWN_NAME = "Java does not know that name. Check spelling and capital letters (Java is case-sensitive), and that you declared the variable first.";
    private static final String WRONG_TYPE = "You put a value of one type into a variable of another type, for example text into an int.";
    private static final String BRACE = "A curly brace { or } is missing or extra. Every opening brace needs a closing one.";
    private static final String QUOTE = "A piece of text started with a quote (\") but never ended. Add the closing quote.";
    private static final String NOT_STATEMENT = "That line is not a complete instruction. Did you forget System.out.println(...) or an = sign?";
    private static final String RETURN = "Your method must return a value on every path.";
    private static final String INIT = "Give the variable a value before you use it.";
    private static final String DIVIDE = "You divided by zero. Integers cannot be divided by 0.";
    private static final String NULL = "You used a variable that has no object yet (null).";
    private static final String INDEX = "You asked for a position that is not in the array.";
    private static final String RECURSION = "A method kept calling itself without stopping.";
    private static final String MEMORY = "Your program tried to use more memory than the game allows.";

    private static final String[][] CASES = {
        {"Main.java:3: error: ';' expected", SEMICOLON},
        {"Main.java:3: error: cannot find symbol", UNKNOWN_NAME},
        {"Main.java:3: error: incompatible types: String cannot be converted to int", WRONG_TYPE},
        {"Main.java:9: error: reached end of file while parsing", BRACE},
        {"Main.java:4: error: class, interface, enum, or record expected", BRACE},
        {"Main.java:3: error: unclosed string literal", QUOTE},
        {"Main.java:3: error: not a statement", NOT_STATEMENT},
        {"Main.java:5: error: missing return statement", RETURN},
        {"Main.java:5: error: variable x might not have been initialized", INIT},
        {"Exception in thread \"main\" java.lang.ArithmeticException: / by zero", DIVIDE},
        {"java.lang.NullPointerException", NULL},
        {"java.lang.ArrayIndexOutOfBoundsException: Index 5 out of bounds", INDEX},
        {"java.lang.StackOverflowError", RECURSION},
        {"java.lang.OutOfMemoryError: Java heap space", MEMORY}
    };

    @Test
    void mapsEachTableRow() {
        for (String[] row : CASES) {
            assertEquals(List.of(row[1]), ErrorTranslator.translate(row[0], ""), row[0]);
        }
    }

    @Test
    void stderrIsScannedToo() {
        assertEquals(List.of(DIVIDE), ErrorTranslator.translate("", "java.lang.ArithmeticException: / by zero"));
    }

    @Test
    void returnsAtMostTwoHintsInTableOrder() {
        assertEquals(List.of(SEMICOLON, UNKNOWN_NAME),
                ErrorTranslator.translate("';' expected\ncannot find symbol\nincompatible types", ""));
    }

    @Test
    void noMatchGivesEmptyList() {
        assertTrue(ErrorTranslator.translate("all good", "").isEmpty());
    }
}
