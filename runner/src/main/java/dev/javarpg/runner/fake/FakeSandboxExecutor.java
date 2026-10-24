package dev.javarpg.runner.fake;

import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Test double: records requests and returns canned results. Never runs code. */
public final class FakeSandboxExecutor implements SandboxExecutor {

    private final List<SandboxRequest> requests = new ArrayList<>();
    private SandboxRequest lastRequest;
    private Function<SandboxRequest, SandboxResult> handler = request -> ok("");

    @Override
    public SandboxResult execute(SandboxRequest request) {
        Function<SandboxRequest, SandboxResult> current;
        synchronized (this) {
            requests.add(request);
            lastRequest = request;
            current = handler;
        }
        // Called outside the lock so a blocking handler cannot stall other callers.
        return current.apply(request);
    }

    public synchronized SandboxRequest lastRequest() {
        return lastRequest;
    }

    public synchronized List<SandboxRequest> requests() {
        return List.copyOf(requests);
    }

    public synchronized void setResult(SandboxResult result) {
        handler = request -> result;
    }

    public synchronized void setHandler(Function<SandboxRequest, SandboxResult> newHandler) {
        handler = newHandler;
    }

    public static SandboxResult ok(String stdout) {
        return new SandboxResult(SandboxStatus.OK, 0, "", stdout, "", false, 5L);
    }

    public static SandboxResult compileError(String compilerOutput) {
        return new SandboxResult(SandboxStatus.COMPILE_ERROR, 100, compilerOutput, "", "", false, 5L);
    }

    public static SandboxResult timeout() {
        return new SandboxResult(SandboxStatus.TIMEOUT, 124, "", "", "", false, 3000L);
    }

    public static SandboxResult outputLimit() {
        return new SandboxResult(SandboxStatus.OUTPUT_LIMIT, 137, "", "", "", true, 1000L);
    }

    public static SandboxResult memoryLimit() {
        return new SandboxResult(SandboxStatus.MEMORY_LIMIT, 137, "", "",
                "java.lang.OutOfMemoryError: Java heap space", false, 1000L);
    }

    public static SandboxResult runtimeError(String stderr) {
        return new SandboxResult(SandboxStatus.RUNTIME_ERROR, 1, "", "", stderr, false, 10L);
    }

    public static SandboxResult sandboxError(String message) {
        return new SandboxResult(SandboxStatus.SANDBOX_ERROR, -1, "", "", message, false, 0L);
    }
}
