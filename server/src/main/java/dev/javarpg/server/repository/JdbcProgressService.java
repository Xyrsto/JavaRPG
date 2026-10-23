package dev.javarpg.server.repository;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.contract.PlayerProgress;
import dev.javarpg.core.contract.ProgressService;
import dev.javarpg.core.contract.QuestRecord;
import dev.javarpg.core.contract.RecordedAttempt;
import dev.javarpg.core.rules.Curriculum;
import dev.javarpg.core.rules.LevelRules;
import dev.javarpg.core.rules.XpRules;
import dev.javarpg.core.world.Point;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC implementation of ProgressService on the H2 tables in schema.sql. */
@Service
public class JdbcProgressService implements ProgressService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public JdbcProgressService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public String createPlayer(Point heroStart) {
        String playerId = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO player (id, hero_x, hero_y, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?)",
                playerId, heroStart.x(), heroStart.y(), now, now);
        return playerId;
    }

    @Override
    public PlayerProgress getPlayerProgress(String playerId) {
        Point hero = loadHero(playerId);
        jdbc.update("UPDATE player SET last_seen_at = ? WHERE id = ?", Timestamp.from(clock.instant()), playerId);
        int xp = xpOf(playerId);
        int level = LevelRules.levelForXp(xp);
        return new PlayerProgress(playerId, xp, level, XpRules.xpFloorForLevel(level),
                LevelRules.nextLevelXp(xp), completedIds(playerId), hero);
    }

    @Override
    public QuestRecord getQuestRecord(String playerId, String questId) {
        loadHero(playerId);
        List<QuestRecord> rows = jdbc.query(
                "SELECT failed_attempts, hints_unlocked, completed, xp_awarded, last_source "
                        + "FROM quest_progress WHERE player_id = ? AND quest_id = ?",
                (rs, rowNum) -> new QuestRecord(questId, rs.getInt("failed_attempts"),
                        rs.getInt("hints_unlocked"), rs.getBoolean("completed"),
                        rs.getInt("xp_awarded"), rs.getString("last_source")),
                playerId, questId);
        return rows.isEmpty() ? new QuestRecord(questId, 0, 0, false, 0, null) : rows.get(0);
    }

    @Override
    public RecordedAttempt recordAttempt(String playerId, String questId, String source, boolean passed,
                                         boolean complete, int xpOnFirstCompletion) {
        return transactions.execute(status -> {
            loadHero(playerId);
            QuestRecord before = getQuestRecord(playerId, questId);
            int xpBefore = xpOf(playerId);
            boolean firstCompletion = passed && complete && !before.completed();
            int xpAwarded = firstCompletion ? xpOnFirstCompletion : 0;
            int failed = before.failedAttempts() + (passed ? 0 : 1);
            boolean completed = before.completed() || firstCompletion;
            Timestamp now = Timestamp.from(clock.instant());

            if (rowExists(playerId, questId)) {
                jdbc.update("UPDATE quest_progress SET failed_attempts = ?, completed = ?, "
                                + "xp_awarded = xp_awarded + ?, last_source = ? WHERE player_id = ? AND quest_id = ?",
                        failed, completed, xpAwarded, source, playerId, questId);
            } else {
                jdbc.update("INSERT INTO quest_progress (player_id, quest_id, failed_attempts, hints_unlocked, "
                                + "completed, xp_awarded, last_source) VALUES (?, ?, ?, 0, ?, ?, ?)",
                        playerId, questId, failed, completed, xpAwarded, source);
            }
            if (firstCompletion) {
                jdbc.update("UPDATE quest_progress SET completed_at = ? WHERE player_id = ? AND quest_id = ?",
                        now, playerId, questId);
            }

            int xpAfter = xpOf(playerId);
            int levelAfter = LevelRules.levelForXp(xpAfter);
            boolean levelUp = levelAfter > LevelRules.levelForXp(xpBefore);
            return new RecordedAttempt(firstCompletion, xpAwarded, xpAfter, levelAfter, levelUp);
        });
    }

    @Override
    public int unlockNextHint(String playerId, String questId, int maxHints) {
        return transactions.execute(status -> {
            loadHero(playerId);
            QuestRecord record = getQuestRecord(playerId, questId);
            if (record.failedAttempts() == 0 && !record.completed()) {
                throw new GameException(ErrorCode.HINT_NOT_AVAILABLE,
                        "Give the challenge a try first, then ask for a hint.");
            }
            if (record.hintsUnlocked() >= maxHints) {
                throw new GameException(ErrorCode.NO_MORE_HINTS,
                        "You have seen all the hints for this challenge.");
            }
            int next = record.hintsUnlocked() + 1;
            jdbc.update("UPDATE quest_progress SET hints_unlocked = ? WHERE player_id = ? AND quest_id = ?",
                    next, playerId, questId);
            return next;
        });
    }

    @Override
    public void saveHeroPosition(String playerId, Point position) {
        int rows = jdbc.update("UPDATE player SET hero_x = ?, hero_y = ?, last_seen_at = ? WHERE id = ?",
                position.x(), position.y(), Timestamp.from(clock.instant()), playerId);
        if (rows == 0) {
            throw unknownPlayer();
        }
    }

    private Point loadHero(String playerId) {
        List<Point> rows = jdbc.query("SELECT hero_x, hero_y FROM player WHERE id = ?",
                (rs, rowNum) -> new Point(rs.getInt("hero_x"), rs.getInt("hero_y")), playerId);
        if (rows.isEmpty()) {
            throw unknownPlayer();
        }
        return rows.get(0);
    }

    private int xpOf(String playerId) {
        Integer xp = jdbc.queryForObject(
                "SELECT COALESCE(SUM(xp_awarded), 0) FROM quest_progress WHERE player_id = ?",
                Integer.class, playerId);
        return xp == null ? 0 : xp;
    }

    private List<String> completedIds(String playerId) {
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

    private static GameException unknownPlayer() {
        return new GameException(ErrorCode.PLAYER_NOT_FOUND,
                "We could not find that player. Start a new game.");
    }
}
