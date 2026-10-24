package dev.javarpg.server.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.runner.fake.FakeSandboxExecutor;
import dev.javarpg.runner.local.UnsafeLocalSandboxExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** F-12a and F-12b: unsafe modes must not start by accident. Tests the config class directly. */
class SandboxSafetyTest {

    @Test
    void unsafeLocalWithoutAllowUnsafeFailsToStart() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> SandboxSafety.requireStartAllowed("unsafe-local", false, "127.0.0.1", false));
        assertTrue(e.getMessage().contains("allow-unsafe"), e.getMessage());
    }

    @Test
    void unsafeLocalNeedsALoopbackBindAddress() {
        for (String address : new String[] {null, "", "0.0.0.0", "::", "192.168.1.10", "example.com"}) {
            assertThrows(IllegalStateException.class,
                    () -> SandboxSafety.requireStartAllowed("unsafe-local", true, address, false), "" + address);
        }
        for (String address : new String[] {"127.0.0.1", "::1", "[::1]", "localhost", "127.0.0.2"}) {
            assertDoesNotThrow(() -> SandboxSafety.requireStartAllowed("unsafe-local", true, address, false), address);
        }
    }

    @Test
    void fakeModeNeedsTheTestProfile() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> SandboxSafety.requireStartAllowed("fake", false, "127.0.0.1", false));
        assertTrue(e.getMessage().contains("test"), e.getMessage());
        assertDoesNotThrow(() -> SandboxSafety.requireStartAllowed("fake", false, "127.0.0.1", true));
    }

    @Test
    void dockerNeverNeedsExtraFlags() {
        assertDoesNotThrow(() -> SandboxSafety.requireStartAllowed("docker", false, null, false));
    }

    @Test
    void onlyDockerIsSafeAndOtherModesCarryAWarning() {
        assertTrue(SandboxSafety.isSafe("docker"));
        assertFalse(SandboxSafety.isSafe("fake"));
        assertFalse(SandboxSafety.isSafe("unsafe-local"));
        assertNull(SandboxSafety.warningFor("docker"));
        assertNotNull(SandboxSafety.warningFor("fake"));
        assertTrue(SandboxSafety.warningFor("unsafe-local").contains("UNSAFE"));
    }

    @Test
    void sandboxConfigRefusesToBuildUnsafeLocalWithoutTheFlag() {
        JavaRpgProperties props = new JavaRpgProperties();
        props.getSandbox().setMode("unsafe-local");
        MockEnvironment env = new MockEnvironment().withProperty("server.address", "127.0.0.1");
        assertThrows(IllegalStateException.class, () -> new SandboxConfig().sandboxExecutor(props, env));

        props.getSandbox().setAllowUnsafe(true);
        assertInstanceOf(UnsafeLocalSandboxExecutor.class, new SandboxConfig().sandboxExecutor(props, env));

        MockEnvironment open = new MockEnvironment().withProperty("server.address", "0.0.0.0");
        assertThrows(IllegalStateException.class, () -> new SandboxConfig().sandboxExecutor(props, open));
        assertThrows(IllegalStateException.class,
                () -> new SandboxConfig().sandboxExecutor(props, new MockEnvironment()));
    }

    @Test
    void sandboxConfigRefusesFakeOutsideTheTestProfile() {
        JavaRpgProperties props = new JavaRpgProperties();
        props.getSandbox().setMode("fake");
        assertThrows(IllegalStateException.class,
                () -> new SandboxConfig().sandboxExecutor(props, new MockEnvironment()));
        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test");
        assertInstanceOf(FakeSandboxExecutor.class, new SandboxConfig().sandboxExecutor(props, test));
    }
}
