package dev.javarpg.server.service;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.server.config.JavaRpgProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bounds the number of player rows (javarpg.players.max) and purges players idle for more than
 * javarpg.players.idle-days at startup (F-14).
 */
@Service
public class PlayerLimits {

    private static final Logger LOG = Logger.getLogger(PlayerLimits.class.getName());

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final JavaRpgProperties props;

    public PlayerLimits(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock,
                        JavaRpgProperties props) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.props = props;
    }

    /** Creates a player unless the table is full, in which case it throws SERVER_BUSY (HTTP 503). */
    public synchronized String createWithinCap(Supplier<String> creator) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM player", Integer.class);
        if (count != null && count >= props.getPlayers().getMax()) {
            throw new GameException(ErrorCode.SERVER_BUSY,
                    "The village is full right now. Try again later.");
        }
        return creator.get();
    }

    /** Deletes players (and their progress) not seen for idle-days. Returns the number of players removed. */
    public int purgeIdle() {
        int days = props.getPlayers().getIdleDays();
        if (days <= 0) {
            return 0;
        }
        Timestamp cutoff = Timestamp.from(clock.instant().minus(Duration.ofDays(days)));
        Integer removed = transactions.execute(status -> {
            jdbc.update("DELETE FROM quest_progress WHERE player_id IN "
                    + "(SELECT id FROM player WHERE last_seen_at < ?)", cutoff);
            return jdbc.update("DELETE FROM player WHERE last_seen_at < ?", cutoff);
        });
        return removed == null ? 0 : removed;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void purgeAtStartup() {
        int removed = purgeIdle();
        if (removed > 0) {
            LOG.info("Purged " + removed + " players idle for more than " + props.getPlayers().getIdleDays() + " days");
        }
    }
}
