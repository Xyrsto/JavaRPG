package dev.javarpg.server.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.runner.fake.FakeSandboxExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SandboxExecutorFactoryTest {

    private final SandboxExecutorFactory factory = new SandboxExecutorFactory();

    @Test
    void unknownModeFailsFastAndNamesTheValidOnes() {
        JavaRpgProperties props = new JavaRpgProperties();
        props.getSandbox().setMode("podman");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> factory.create(props, new MockEnvironment()));
        assertTrue(e.getMessage().contains("'podman'") && e.getMessage().contains("docker, unsafe-local or fake"),
                e.getMessage());
    }

    @Test
    void fakeModeIsCreatedOnlyUnderTheTestProfile() {
        JavaRpgProperties props = new JavaRpgProperties();
        props.getSandbox().setMode("fake");
        assertThrows(IllegalStateException.class, () -> factory.create(props, new MockEnvironment()));
        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test");
        assertInstanceOf(FakeSandboxExecutor.class, factory.create(props, test));
    }
}
