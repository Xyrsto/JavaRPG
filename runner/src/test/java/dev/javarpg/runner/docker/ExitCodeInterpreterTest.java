package dev.javarpg.runner.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.javarpg.core.contract.SandboxStatus;
import org.junit.jupiter.api.Test;

class ExitCodeInterpreterTest {

    private static SandboxStatus interpret(int exit, long duration, String stderr, boolean outLimit, boolean watchdog) {
        return ExitCodeInterpreter.interpret(exit, duration, 3000L, stderr, outLimit, watchdog);
    }

    @Test
    void exit100IsCompileError() {
        assertEquals(SandboxStatus.COMPILE_ERROR, interpret(100, 900, "", false, false));
    }

    @Test
    void exit102IsCompileTimeoutNotCompileError() {
        assertEquals(SandboxStatus.TIMEOUT, interpret(102, 10_500, "", false, false));
    }

    @Test
    void exit0IsOk() {
        assertEquals(SandboxStatus.OK, interpret(0, 900, "", false, false));
    }

    @Test
    void exit124IsTimeout() {
        assertEquals(SandboxStatus.TIMEOUT, interpret(124, 3100, "", false, false));
    }

    @Test
    void exit137WithLongDurationIsTimeout() {
        assertEquals(SandboxStatus.TIMEOUT, interpret(137, 4000, "", false, false));
    }

    @Test
    void exit137WithShortDurationIsMemoryLimit() {
        assertEquals(SandboxStatus.MEMORY_LIMIT, interpret(137, 500, "", false, false));
    }

    @Test
    void outOfMemoryErrorInStderrIsMemoryLimit() {
        assertEquals(SandboxStatus.MEMORY_LIMIT,
                interpret(1, 500, "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space", false, false));
    }

    @Test
    void otherNonZeroExitIsRuntimeError() {
        assertEquals(SandboxStatus.RUNTIME_ERROR,
                interpret(1, 500, "Exception in thread \"main\" java.lang.ArithmeticException: / by zero", false, false));
    }

    @Test
    void watchdogFiredBeatsEverything() {
        assertEquals(SandboxStatus.TIMEOUT, interpret(137, 100, "OutOfMemoryError", true, true));
        assertEquals(SandboxStatus.TIMEOUT, interpret(0, 100, "", false, true));
    }

    @Test
    void stdoutLimitGivesOutputLimit() {
        assertEquals(SandboxStatus.OUTPUT_LIMIT, interpret(137, 500, "", true, false));
        assertEquals(SandboxStatus.OUTPUT_LIMIT, interpret(0, 500, "", true, false));
    }
}
