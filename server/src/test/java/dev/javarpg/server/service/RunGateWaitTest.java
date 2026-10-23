package dev.javarpg.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.server.config.JavaRpgProperties;
import org.junit.jupiter.api.Test;

/** F-14: the default acquire wait is 0 ms, so a busy runner answers SERVER_BUSY immediately. */
class RunGateWaitTest {

    @Test
    void defaultWaitIsZero() {
        assertEquals(0L, new JavaRpgProperties().getSandbox().getAcquireWaitMillis());
    }

    @Test
    void whenBothSlotsAreBusyTheThirdRunIsRejectedImmediately() {
        RunGate gate = new RunGate(2, new JavaRpgProperties().getSandbox().getAcquireWaitMillis());
        gate.acquire("a");
        gate.acquire("b");
        long start = System.nanoTime();
        GameException e = assertThrows(GameException.class, () -> gate.acquire("c"));
        assertEquals(ErrorCode.SERVER_BUSY, e.code());
        assertTrue((System.nanoTime() - start) / 1_000_000L < 500L, "should not wait");
        gate.release("a");
        gate.release("b");
    }
}
