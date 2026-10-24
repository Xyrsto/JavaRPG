package dev.javarpg.server.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.PlayerProgress;
import dev.javarpg.core.contract.QuestRecord;
import dev.javarpg.core.world.Point;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class PersistenceRestartTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;

    @Test
    void progressIsIntactAfterTheDatabaseIsClosedAndOpenedAgain() {
        String url = "jdbc:h2:file:" + tmp.resolve("javarpg").toAbsolutePath();

        DriverManagerDataSource firstDs = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(firstDs);
        JdbcTemplate firstJdbc = new JdbcTemplate(firstDs);
        JdbcProgressService first = new JdbcProgressService(firstJdbc, new DataSourceTransactionManager(firstDs), CLOCK);
        String playerId = first.createPlayer(new Point(5, 4));
        first.saveHeroPosition(playerId, new Point(6, 4));
        first.recordAttempt(playerId, "V01", "saved source", true, true, 100);
        firstJdbc.execute("SHUTDOWN");

        DriverManagerDataSource secondDs = new DriverManagerDataSource(url, "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(secondDs);
        JdbcProgressService second = new JdbcProgressService(new JdbcTemplate(secondDs),
                new DataSourceTransactionManager(secondDs), CLOCK);

        PlayerProgress progress = second.getPlayerProgress(playerId);
        assertEquals(100, progress.xp());
        assertEquals(List.of("V01"), progress.completedQuestIds());
        assertEquals(new Point(6, 4), progress.hero());

        QuestRecord record = second.getQuestRecord(playerId, "V01");
        assertTrue(record.completed());
        assertEquals("saved source", record.lastSource());
        assertEquals(100, record.xpAwarded());
    }
}
