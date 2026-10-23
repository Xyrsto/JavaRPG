package dev.javarpg.server.repository;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.contract.PlayerProgress;
import dev.javarpg.core.contract.ProgressService;
import dev.javarpg.core.contract.QuestRecord;
import dev.javarpg.core.contract.RecordedAttempt;
import dev.javarpg.core.rules.LevelRules;
import dev.javarpg.core.rules.XpRules;
import dev.javarpg.core.world.Point;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ProgressService on the H2 tables in schema.sql. This class owns the progress rules and the transaction
 * boundaries; the SQL lives in PlayerTable and QuestProgressTable.
 */
@Service
public class JdbcProgressService implements ProgressService {

    private final PlayerTable players;
    private final QuestProgressTable quests;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public JdbcProgressService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.players = new PlayerTable(jdbc, clock);
        this.quests = new QuestProgressTable(jdbc);
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public String createPlayer(Point heroStart) {
        return players.insert(heroStart);
    }

    @Override
    public PlayerProgress getPlayerProgress(String playerId) {
        Point hero = players.loadHero(playerId);
        players.touch(playerId);
        int xp = quests.totalXp(playerId);
        int level = LevelRules.levelForXp(xp);
        return new PlayerProgress(playerId, xp, level, XpRules.xpFloorForLevel(level),
                LevelRules.nextLevelXp(xp), quests.completedIds(playerId), hero);
    }

    @Override
    public QuestRecord getQuestRecord(String playerId, String questId) {
        players.loadHero(playerId);
        return quests.find(playerId, questId);
    }

    @Override
    public RecordedAttempt recordAttempt(String playerId, String questId, String source, boolean passed,
                                         boolean complete, int xpOnFirstCompletion) {
        return transactions.execute(status -> {
            players.loadHero(playerId);
            QuestRecord before = quests.find(playerId, questId);
            int xpBefore = quests.totalXp(playerId);
            boolean firstCompletion = passed && complete && !before.completed();
            int xpAwarded = firstCompletion ? xpOnFirstCompletion : 0;
            int failed = before.failedAttempts() + (passed ? 0 : 1);
            boolean completed = before.completed() || firstCompletion;

            quests.upsertAttempt(playerId, questId, failed, completed, xpAwarded, source);
            if (firstCompletion) {
                quests.markCompletedAt(playerId, questId, Timestamp.from(clock.instant()));
            }

            int xpAfter = quests.totalXp(playerId);
            int levelAfter = LevelRules.levelForXp(xpAfter);
            boolean levelUp = levelAfter > LevelRules.levelForXp(xpBefore);
            return new RecordedAttempt(firstCompletion, xpAwarded, xpAfter, levelAfter, levelUp);
        });
    }

    @Override
    public int unlockNextHint(String playerId, String questId, int maxHints) {
        return transactions.execute(status -> {
            players.loadHero(playerId);
            QuestRecord record = quests.find(playerId, questId);
            if (record.failedAttempts() == 0 && !record.completed()) {
                throw new GameException(ErrorCode.HINT_NOT_AVAILABLE,
                        "Give the challenge a try first, then ask for a hint.");
            }
            if (record.hintsUnlocked() >= maxHints) {
                throw new GameException(ErrorCode.NO_MORE_HINTS,
                        "You have seen all the hints for this challenge.");
            }
            int next = record.hintsUnlocked() + 1;
            quests.saveHintsUnlocked(playerId, questId, next);
            return next;
        });
    }

    @Override
    public void saveHeroPosition(String playerId, Point position) {
        players.saveHero(playerId, position);
    }
}
