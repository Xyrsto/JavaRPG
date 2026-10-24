package dev.javarpg.server.repository;

import dev.javarpg.core.contract.QuestRecord;
import dev.javarpg.core.rules.Curriculum;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** SQL for the quest_progress table: one row per player and quest. */
final class QuestProgressTable {

    private final JdbcTemplate jdbc;

    QuestProgressTable(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The stored record, or an empty record (nothing tried yet) when there is no row. */
    QuestRecord find(String playerId, String questId) {
        List<QuestRecord> rows = jdbc.query(
                "SELECT failed_attempts, hints_unlocked, completed, xp_awarded, last_source "
                        + "FROM quest_progress WHERE player_id = ? AND quest_id = ?",
                (rs, rowNum) -> new QuestRecord(questId, rs.getInt("failed_attempts"),
                        rs.getInt("hints_unlocked"), rs.getBoolean("completed"),
                        rs.getInt("xp_awarded"), rs.getString("last_source")),
                playerId, questId);
        return rows.isEmpty() ? new QuestRecord(questId, 0, 0, false, 0, null) : rows.get(0);
    }

    void upsertAttempt(String playerId, String questId, int failedAttempts, boolean completed, int xpAwarded,
                       String source) {
        if (rowExists(playerId, questId)) {
            jdbc.update("UPDATE quest_progress SET failed_attempts = ?, completed = ?, "
                            + "xp_awarded = xp_awarded + ?, last_source = ? WHERE player_id = ? AND quest_id = ?",
                    failedAttempts, completed, xpAwarded, source, playerId, questId);
        } else {
            jdbc.update("INSERT INTO quest_progress (player_id, quest_id, failed_attempts, hints_unlocked, "
                            + "completed, xp_awarded, last_source) VALUES (?, ?, ?, 0, ?, ?, ?)",
                    playerId, questId, failedAttempts, completed, xpAwarded, source);
        }
    }

    void markCompletedAt(String playerId, String questId, Timestamp when) {
        jdbc.update("UPDATE quest_progress SET completed_at = ? WHERE player_id = ? AND quest_id = ?",
                when, playerId, questId);
    }

    void saveHintsUnlocked(String playerId, String questId, int hintsUnlocked) {
        jdbc.update("UPDATE quest_progress SET hints_unlocked = ? WHERE player_id = ? AND quest_id = ?",
                hintsUnlocked, playerId, questId);
    }

    int totalXp(String playerId) {
        Integer xp = jdbc.queryForObject(
                "SELECT COALESCE(SUM(xp_awarded), 0) FROM quest_progress WHERE player_id = ?",
                Integer.class, playerId);
        return xp == null ? 0 : xp;
    }

    /** Completed quest ids in curriculum order. */
    List<String> completedIds(String playerId) {
        List<String> ids = new ArrayList<>(jdbc.queryForList(
                "SELECT quest_id FROM quest_progress WHERE player_id = ? AND completed = TRUE",
                String.class, playerId));
        ids.sort(Comparator.comparingInt(Curriculum::globalIndex));
        return ids;
    }

    private boolean rowExists(String playerId, String questId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM quest_progress WHERE player_id = ? AND quest_id = ?",
                Integer.class, playerId, questId);
        return count != null && count > 0;
    }
}
