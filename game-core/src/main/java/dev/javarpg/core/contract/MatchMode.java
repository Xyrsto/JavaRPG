package dev.javarpg.core.contract;

/**
 * EXACT_TRIMMED : normalized stdout (STDOUT) or returned value text (METHOD_RETURN) equals expected after normalization.
 * CONTAINS      : normalized stdout contains expected (STDOUT only).
 * NOT_CONTAINS  : normalized stdout does not contain expected (STDOUT only).
 * SOURCE_REGEX  : expected is a Java regex that must be found (Matcher.find) in the player source with comments removed.
 */
public enum MatchMode { EXACT_TRIMMED, CONTAINS, NOT_CONTAINS, SOURCE_REGEX }
