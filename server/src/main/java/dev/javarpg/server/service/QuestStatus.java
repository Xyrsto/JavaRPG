package dev.javarpg.server.service;

/** A quest as one player sees it. The enum name is the API value (QuestSummary.status and friends). */
public enum QuestStatus {
    LOCKED,
    AVAILABLE,
    COMPLETED
}
