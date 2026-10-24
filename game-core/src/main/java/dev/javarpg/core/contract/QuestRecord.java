package dev.javarpg.core.contract;

/** Raw stored state for one player+quest. A quest never touched has zeros, completed=false, lastSource=null (nullable). */
public record QuestRecord(
        String questId,
        int failedAttempts,
        int hintsUnlocked,
        boolean completed,
        int xpAwarded,
        String lastSource) { }
