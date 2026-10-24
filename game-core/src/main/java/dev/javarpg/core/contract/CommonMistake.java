package dev.javarpg.core.contract;

/** If the Java regex `pattern` is found in the chosen text, `message` is shown as a friendly hint. */
public record CommonMistake(MistakeSource source, String pattern, String message) { }
