package dev.javarpg.core.contract;

/** Compiles and runs untrusted code in isolation. MUST NOT throw for player faults; infrastructure faults return SANDBOX_ERROR. */
public interface SandboxExecutor {
    SandboxResult execute(SandboxRequest request);
}
