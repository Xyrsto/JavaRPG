package dev.javarpg.core.contract;

/** firstCompletion is true only the one time the quest becomes completed; xpAwarded is 0 otherwise. */
public record RecordedAttempt(
        boolean firstCompletion,
        int xpAwarded,
        int totalXp,
        int level,
        boolean levelUp) { }
