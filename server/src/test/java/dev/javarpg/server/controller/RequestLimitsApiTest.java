package dev.javarpg.server.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.javarpg.server.service.PlayerLimits;
import dev.javarpg.server.support.TestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** F-12, F-13, F-14 through the whole Spring stack (filters, advice, services). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestLimitsApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PlayerLimits limits;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void oversizedRunBodyIsRefusedWith413AndTheGenericEnvelope() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        String huge = "a".repeat(70_000);
        TestSupport.run(mvc, playerId, "V01", huge)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("SOURCE_TOO_LARGE"))
                .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    @Test
    void oversizedSubmitAndMoveBodiesAreRefusedToo() throws Exception {
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.submit(mvc, playerId, "V01", "b".repeat(100_000)).andExpect(status().isPayloadTooLarge());
        String[] many = new String[20_000];
        java.util.Arrays.fill(many, "UP");
        TestSupport.move(mvc, playerId, many).andExpect(status().isPayloadTooLarge());
    }

    @Test
    void healthReportsFakeModeAsNotSafeWithAWarning() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sandbox").value("fake"))
                .andExpect(jsonPath("$.sandboxSafe").value(false))
                .andExpect(jsonPath("$.warning").isNotEmpty());
    }

    @Test
    void idlePlayersArePurgedAndRecentOnesKept() {
        String idle = UUID.randomUUID().toString();
        String recent = UUID.randomUUID().toString();
        Timestamp old = Timestamp.from(Instant.now().minus(60, ChronoUnit.DAYS));
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO player (id, hero_x, hero_y, created_at, last_seen_at) VALUES (?, 1, 1, ?, ?)",
                idle, old, old);
        jdbc.update("INSERT INTO player (id, hero_x, hero_y, created_at, last_seen_at) VALUES (?, 1, 1, ?, ?)",
                recent, now, now);
        jdbc.update("INSERT INTO quest_progress (player_id, quest_id) VALUES (?, 'V01')", idle);
        assertTrue(limits.purgeIdle() >= 1);
        assertEquals(0, count("SELECT COUNT(*) FROM player WHERE id = ?", idle));
        assertEquals(0, count("SELECT COUNT(*) FROM quest_progress WHERE player_id = ?", idle));
        assertEquals(1, count("SELECT COUNT(*) FROM player WHERE id = ?", recent));
    }

    private int count(String sql, String id) {
        Integer n = jdbc.queryForObject(sql, Integer.class, id);
        return n == null ? -1 : n;
    }
}
