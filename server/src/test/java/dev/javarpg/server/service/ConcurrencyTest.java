package dev.javarpg.server.service;

import static dev.javarpg.server.support.TestSupport.V01_SOLUTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.server.support.TestSupport;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrencyTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SandboxExecutor executor;

    @BeforeEach
    void resetSandbox() {
        TestSupport.reset(executor);
    }

    @Test
    void secondAcquireForTheSamePlayerIsRejected() {
        RunGate gate = new RunGate(2, 100);
        gate.acquire("player-a");
        GameException e = assertThrows(GameException.class, () -> gate.acquire("player-a"));
        assertEquals(ErrorCode.RUN_IN_PROGRESS, e.code());
        gate.release("player-a");
    }

    @Test
    void differentPlayersProceedUpToTheGlobalLimit() {
        RunGate gate = new RunGate(2, 100);
        gate.acquire("player-a");
        gate.acquire("player-b");
        GameException e = assertThrows(GameException.class, () -> gate.acquire("player-c"));
        assertEquals(ErrorCode.SERVER_BUSY, e.code());
        gate.release("player-a");
        gate.release("player-b");
    }

    @Test
    void releaseFreesTheSlot() {
        RunGate gate = new RunGate(1, 100);
        gate.acquire("player-a");
        gate.release("player-a");
        gate.acquire("player-b");
        gate.release("player-b");
    }

    @Test
    void theSlotIsFreedEvenWhenTheRunThrows() {
        RunGate gate = new RunGate(1, 100);
        try {
            gate.acquire("player-a");
            try {
                throw new IllegalStateException("run failed");
            } finally {
                gate.release("player-a");
            }
        } catch (IllegalStateException expected) {
            assertEquals("run failed", expected.getMessage());
        }
        gate.acquire("player-a");
        gate.release("player-a");
    }

    @Test
    void sameplayerSubmitWhileARunIsInProgressGetsA429() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        TestSupport.program(executor, request -> {
            started.countDown();
            try {
                finish.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return TestSupport.ok(TestSupport.V01_OUTPUT);
        });
        String playerId = TestSupport.createPlayer(mvc);
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> {
            try {
                return TestSupport.submit(mvc, playerId, "V01", V01_SOLUTION).andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS), "first run never started");
            TestSupport.submit(mvc, playerId, "V01", V01_SOLUTION)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("RUN_IN_PROGRESS"));
        } finally {
            finish.countDown();
        }
        assertEquals(200, first.get(10, TimeUnit.SECONDS).intValue());
    }
}
