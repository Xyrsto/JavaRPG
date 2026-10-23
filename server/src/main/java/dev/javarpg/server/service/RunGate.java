package dev.javarpg.server.service;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Limits code runs: one run per player at a time (RUN_IN_PROGRESS) and a global number of
 * concurrent sandboxes (SERVER_BUSY after the acquire timeout). Every successful acquire must be
 * paired with exactly one release, normally in a finally block.
 */
public final class RunGate {

    private final Semaphore slots;
    private final Set<String> activePlayers = ConcurrentHashMap.newKeySet();
    private final long acquireTimeoutMillis;

    public RunGate(int maxConcurrent, long acquireTimeoutMillis) {
        this.slots = new Semaphore(maxConcurrent, true);
        this.acquireTimeoutMillis = acquireTimeoutMillis;
    }

    public void acquire(String playerId) {
        if (!activePlayers.add(playerId)) {
            throw new GameException(ErrorCode.RUN_IN_PROGRESS,
                    "One of your runs is still going. Wait for it to finish.");
        }
        boolean granted;
        try {
            granted = slots.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            granted = false;
        }
        if (!granted) {
            activePlayers.remove(playerId);
            throw new GameException(ErrorCode.SERVER_BUSY,
                    "The code runner is busy right now. Try again in a moment.");
        }
    }

    public void release(String playerId) {
        activePlayers.remove(playerId);
        slots.release();
    }
}
