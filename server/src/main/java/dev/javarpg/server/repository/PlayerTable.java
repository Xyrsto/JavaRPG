package dev.javarpg.server.repository;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.world.Point;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** SQL for the player table: identity, hero position and last-seen time. */
final class PlayerTable {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    PlayerTable(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    String insert(Point heroStart) {
        String playerId = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO player (id, hero_x, hero_y, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?)",
                playerId, heroStart.x(), heroStart.y(), now, now);
        return playerId;
    }

    /** Hero position, or GameException(PLAYER_NOT_FOUND). Also serves as the "player exists" check. */
    Point loadHero(String playerId) {
        List<Point> rows = jdbc.query("SELECT hero_x, hero_y FROM player WHERE id = ?",
                (rs, rowNum) -> new Point(rs.getInt("hero_x"), rs.getInt("hero_y")), playerId);
        if (rows.isEmpty()) {
            throw unknownPlayer();
        }
        return rows.get(0);
    }

    void touch(String playerId) {
        jdbc.update("UPDATE player SET last_seen_at = ? WHERE id = ?", Timestamp.from(clock.instant()), playerId);
    }

    void saveHero(String playerId, Point position) {
        int rows = jdbc.update("UPDATE player SET hero_x = ?, hero_y = ?, last_seen_at = ? WHERE id = ?",
                position.x(), position.y(), Timestamp.from(clock.instant()), playerId);
        if (rows == 0) {
            throw unknownPlayer();
        }
    }

    private static GameException unknownPlayer() {
        return new GameException(ErrorCode.PLAYER_NOT_FOUND,
                "We could not find that player. Start a new game.");
    }
}
