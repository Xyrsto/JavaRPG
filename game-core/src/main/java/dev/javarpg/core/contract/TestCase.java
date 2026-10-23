package dev.javarpg.core.contract;

/**
 * name     : shown to the player for visible tests; hidden tests are shown as "Hidden test N".
 * call     : nullable. METHOD_RETURN only: a Java expression such as "Solution.add(2, 3)". null for STDOUT.
 * expected : expected text (stdout fragment, returned value text, or regex for SOURCE_REGEX).
 * match    : comparison mode.
 */
public record TestCase(String name, String call, String expected, MatchMode match) { }
