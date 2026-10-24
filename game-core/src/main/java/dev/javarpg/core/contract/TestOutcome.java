package dev.javarpg.core.contract;

/**
 * For hidden tests: name is "Hidden test N", expected and actual are null, message is generic. Hidden data never leaves the server.
 * For visible tests expected/actual may be null for SOURCE_REGEX tests.
 */
public record TestOutcome(String name, boolean hidden, boolean passed, String expected, String actual, String message) { }
