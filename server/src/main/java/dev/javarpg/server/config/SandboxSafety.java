package dev.javarpg.server.config;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Start-up safety rules for the sandbox mode (security review F-12a, F-12b). Pure functions so the
 * rules can be unit-tested without a Spring context.
 */
public final class SandboxSafety {

    public static final String UNSAFE_LOCAL = "unsafe-local";
    public static final String FAKE = "fake";
    public static final String DOCKER = "docker";

    private SandboxSafety() {
    }

    /** Throws IllegalStateException with a clear message when the mode must not start. */
    public static void requireStartAllowed(String mode, boolean allowUnsafe, String bindAddress,
                                           boolean testProfileActive) {
        if (UNSAFE_LOCAL.equals(mode)) {
            if (!allowUnsafe) {
                throw new IllegalStateException("javarpg.sandbox.mode=unsafe-local runs player code directly on "
                        + "this machine. Refusing to start. If you really want that for local development, also "
                        + "set javarpg.sandbox.allow-unsafe=true.");
            }
            if (!isLoopback(bindAddress)) {
                throw new IllegalStateException("javarpg.sandbox.mode=unsafe-local requires server.address to be "
                        + "a loopback address (127.0.0.1 or ::1) but it is '" + bindAddress + "'. Refusing to start.");
            }
        } else if (FAKE.equals(mode) && !testProfileActive) {
            throw new IllegalStateException("javarpg.sandbox.mode=fake never runs code and is only allowed when "
                    + "the Spring profile 'test' is active. Use docker for real play.");
        }
    }

    /** True for a literal loopback address or 'localhost'. Null, blank and wildcard addresses are not loopback. */
    public static boolean isLoopback(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        String a = address.trim();
        if (a.startsWith("[") && a.endsWith("]")) {
            a = a.substring(1, a.length() - 1);
        }
        if ("localhost".equalsIgnoreCase(a)) {
            return true;
        }
        // Only literal IP addresses are resolved here, so no DNS lookup happens for host names.
        if (!a.matches("[0-9.]+") && !a.matches("[0-9A-Fa-f:.]+")) {
            return false;
        }
        try {
            InetAddress parsed = InetAddress.getByName(a);
            return parsed.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** Only the docker mode counts as a safe sandbox. */
    public static boolean isSafe(String mode) {
        return DOCKER.equals(mode);
    }

    /** Human readable warning for /health, or null when the mode is docker. */
    public static String warningFor(String mode) {
        if (UNSAFE_LOCAL.equals(mode)) {
            return "UNSAFE: player code runs directly on this machine without isolation. "
                    + "Use only on your own computer, never expose this server.";
        }
        if (FAKE.equals(mode)) {
            return "TEST MODE: the fake sandbox never runs code, so grades are meaningless.";
        }
        if (!DOCKER.equals(mode)) {
            return "Unknown sandbox mode.";
        }
        return null;
    }
}
