package dev.javarpg.runner.grading;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Maps javac and runtime error text to plain-language hints. Table order decides priority. */
public final class ErrorTranslator {

    private static final int MAX_HINTS = 2;

    private record Rule(Pattern pattern, String hint) {
    }

    private static final List<Rule> RULES = List.of(
        new Rule(Pattern.compile(Pattern.quote("';' expected")),
            "Java statements end with a semicolon (;). Check the end of the line the error points to."),
        new Rule(Pattern.compile(Pattern.quote("cannot find symbol")),
            "Java does not know that name. Check spelling and capital letters (Java is case-sensitive), and that you declared the variable first."),
        new Rule(Pattern.compile(Pattern.quote("incompatible types")),
            "You put a value of one type into a variable of another type, for example text into an int."),
        new Rule(Pattern.compile("class, interface, enum, or record expected|reached end of file while parsing"),
            "A curly brace { or } is missing or extra. Every opening brace needs a closing one."),
        new Rule(Pattern.compile(Pattern.quote("unclosed string literal")),
            "A piece of text started with a quote (\") but never ended. Add the closing quote."),
        new Rule(Pattern.compile(Pattern.quote("not a statement")),
            "That line is not a complete instruction. Did you forget System.out.println(...) or an = sign?"),
        new Rule(Pattern.compile(Pattern.quote("missing return statement")),
            "Your method must return a value on every path."),
        new Rule(Pattern.compile(Pattern.quote("might not have been initialized")),
            "Give the variable a value before you use it."),
        new Rule(Pattern.compile(Pattern.quote("ArithmeticException")),
            "You divided by zero. Integers cannot be divided by 0."),
        new Rule(Pattern.compile(Pattern.quote("NullPointerException")),
            "You used a variable that has no object yet (null)."),
        new Rule(Pattern.compile(Pattern.quote("ArrayIndexOutOfBoundsException")),
            "You asked for a position that is not in the array."),
        new Rule(Pattern.compile(Pattern.quote("StackOverflowError")),
            "A method kept calling itself without stopping."),
        new Rule(Pattern.compile(Pattern.quote("OutOfMemoryError")),
            "Your program tried to use more memory than the game allows.")
    );

    private ErrorTranslator() {
    }

    public static List<String> translate(String compilerOutput, String stderr) {
        String text = (compilerOutput == null ? "" : compilerOutput) + "\n" + (stderr == null ? "" : stderr);
        List<String> hints = new ArrayList<>();
        for (Rule rule : RULES) {
            if (hints.size() >= MAX_HINTS) {
                break;
            }
            if (rule.pattern().matcher(text).find()) {
                hints.add(rule.hint());
            }
        }
        return List.copyOf(hints);
    }
}
