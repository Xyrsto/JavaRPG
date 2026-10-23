package dev.javarpg.core.contract;

import dev.javarpg.core.world.Point;

/** Implemented in `server` (JDBC). All methods throw GameException(PLAYER_NOT_FOUND) for an unknown playerId. */
public interface ProgressService {
    /** Creates a player at the world's hero start and returns its new UUID string. */
    String createPlayer(Point heroStart);

    PlayerProgress getPlayerProgress(String playerId);

    QuestRecord getQuestRecord(String playerId, String questId);

    /**
     * Always stores lastSource. If !passed increments failedAttempts. If passed && complete and the quest is not yet
     * completed: marks completed and awards xpOnFirstCompletion, in one transaction. Idempotent: never awards twice.
     */
    RecordedAttempt recordAttempt(String playerId, String questId, String source, boolean passed, boolean complete, int xpOnFirstCompletion);

    /**
     * Increments hintsUnlocked and returns the new count (1..maxHints).
     * Throws GameException(HINT_NOT_AVAILABLE) if failedAttempts == 0 and the quest is not completed,
     * GameException(NO_MORE_HINTS) if hintsUnlocked >= maxHints.
     */
    int unlockNextHint(String playerId, String questId, int maxHints);

    void saveHeroPosition(String playerId, Point position);
}
