package dev.javarpg.server.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.contract.PlayerProgress;
import dev.javarpg.core.contract.QuestRecord;
import dev.javarpg.core.contract.RecordedAttempt;
import dev.javarpg.core.world.Point;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class JdbcProgressServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);
    private static final String UNKNOWN = "00000000-0000-4000-8000-000000000000";

    private JdbcProgressService service;

    @BeforeEach
    void setUp() {
        String url = "jdbc:h2:mem:progress" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        DriverManagerDataSource ds = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        service = new JdbcProgressService(new JdbcTemplate(ds), new DataSourceTransactionManager(ds), CLOCK);
    }

    @Test
    void createPlayerStartsAtTheHeroStart() {
        String playerId = service.createPlayer(new Point(5, 4));
        UUID.fromString(playerId);
        PlayerProgress progress = service.getPlayerProgress(playerId);
        assertEquals(0, progress.xp());
        assertEquals(1, progress.level());
        assertEquals(0, progress.levelFloorXp());
        assertEquals(230, progress.nextLevelXp());
        assertEquals(new Point(5, 4), progress.hero());
        assertTrue(progress.completedQuestIds().isEmpty());
    }

    @Test
    void unknownPlayerIsRejectedEverywhere() {
        assertCode(ErrorCode.PLAYER_NOT_FOUND, () -> service.getPlayerProgress(UNKNOWN));
        assertCode(ErrorCode.PLAYER_NOT_FOUND, () -> service.getQuestRecord(UNKNOWN, "V01"));
        assertCode(ErrorCode.PLAYER_NOT_FOUND, () -> service.recordAttempt(UNKNOWN, "V01", "x", false, false, 100));
        assertCode(ErrorCode.PLAYER_NOT_FOUND, () -> service.unlockNextHint(UNKNOWN, "V01", 3));
        assertCode(ErrorCode.PLAYER_NOT_FOUND, () -> service.saveHeroPosition(UNKNOWN, new Point(1, 1)));
    }

    @Test
    void failedAttemptIncrementsCounterAndStoresSource() {
        String playerId = service.createPlayer(new Point(5, 4));
        service.recordAttempt(playerId, "V01", "first try", false, true, 100);
        QuestRecord record = service.getQuestRecord(playerId, "V01");
        assertEquals(1, record.failedAttempts());
        assertEquals("first try", record.lastSource());
        assertFalse(record.completed());
    }

    @Test
    void firstPassAwardsXpOnlyOnce() {
        String playerId = service.createPlayer(new Point(5, 4));
        RecordedAttempt first = service.recordAttempt(playerId, "V01", "ok", true, true, 300);
        assertTrue(first.firstCompletion());
        assertEquals(300, first.xpAwarded());
        assertEquals(300, first.totalXp());

        RecordedAttempt second = service.recordAttempt(playerId, "V01", "ok again", true, true, 300);
        assertFalse(second.firstCompletion());
        assertEquals(0, second.xpAwarded());
        assertEquals(300, second.totalXp());
    }

    @Test
    void aRunThatPassesDoesNotComplete() {
        String playerId = service.createPlayer(new Point(5, 4));
        RecordedAttempt attempt = service.recordAttempt(playerId, "V02", "x", true, false, 100);
        assertFalse(attempt.firstCompletion());
        assertEquals(0, attempt.xpAwarded());
        assertFalse(service.getQuestRecord(playerId, "V02").completed());
    }

    @Test
    void levelUpIsReportedWhenCrossingAThreshold() {
        String playerId = service.createPlayer(new Point(5, 4));
        RecordedAttempt attempt = service.recordAttempt(playerId, "V01", "ok", true, true, 300);
        assertTrue(attempt.levelUp());
        assertEquals(2, attempt.level());
    }

    @Test
    void hintsFollowTheUnlockRules() {
        String playerId = service.createPlayer(new Point(5, 4));
        assertCode(ErrorCode.HINT_NOT_AVAILABLE, () -> service.unlockNextHint(playerId, "V01", 3));

        service.recordAttempt(playerId, "V01", "wrong", false, true, 100);
        assertEquals(1, service.unlockNextHint(playerId, "V01", 3));
        assertEquals(2, service.unlockNextHint(playerId, "V01", 3));
        assertEquals(3, service.unlockNextHint(playerId, "V01", 3));
        assertCode(ErrorCode.NO_MORE_HINTS, () -> service.unlockNextHint(playerId, "V01", 3));
    }

    @Test
    void heroPositionRoundTrips() {
        String playerId = service.createPlayer(new Point(5, 4));
        service.saveHeroPosition(playerId, new Point(3, 3));
        assertEquals(new Point(3, 3), service.getPlayerProgress(playerId).hero());
    }

    private static void assertCode(ErrorCode expected, Runnable action) {
        GameException e = assertThrows(GameException.class, action::run);
        assertEquals(expected, e.code());
    }
}
