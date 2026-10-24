package dev.javarpg.server.config;

import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.runner.docker.DockerCleanup;
import dev.javarpg.runner.docker.DockerSandboxExecutor;
import dev.javarpg.runner.fake.FakeSandboxExecutor;
import dev.javarpg.runner.local.UnsafeLocalSandboxExecutor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Picks the SandboxExecutor for javarpg.sandbox.mode. The start-up safety rules (F-12a, F-12b) run first,
 * and an unknown mode fails fast. A new mode is one more entry in the table below.
 */
final class SandboxExecutorFactory {

    private static final Logger LOG = Logger.getLogger(SandboxExecutorFactory.class.getName());
    private static final String DOCKER_COMMAND = "docker";

    private final Map<String, Function<JavaRpgProperties.Sandbox, SandboxExecutor>> modes = new LinkedHashMap<>();

    SandboxExecutorFactory() {
        modes.put(SandboxSafety.DOCKER, SandboxExecutorFactory::docker);
        modes.put(SandboxSafety.UNSAFE_LOCAL, settings -> unsafeLocal());
        modes.put(SandboxSafety.FAKE, settings -> fake());
    }

    SandboxExecutor create(JavaRpgProperties props, Environment env) {
        JavaRpgProperties.Sandbox settings = props.getSandbox();
        String mode = settings.getMode();
        SandboxSafety.requireStartAllowed(mode, settings.isAllowUnsafe(),
                env.getProperty("server.address"), env.acceptsProfiles(Profiles.of("test")));
        Function<JavaRpgProperties.Sandbox, SandboxExecutor> creator = modes.get(mode);
        if (creator == null) {
            throw new IllegalStateException("Unknown javarpg.sandbox.mode '" + mode
                    + "'. Use docker, unsafe-local or fake.");
        }
        return creator.apply(settings);
    }

    private static SandboxExecutor docker(JavaRpgProperties.Sandbox settings) {
        int removed = DockerCleanup.removeOrphans(DOCKER_COMMAND);
        LOG.info("Docker sandbox enabled; removed " + removed + " orphaned runner containers");
        SandboxContainerReaper.start(DOCKER_COMMAND);
        return new DockerSandboxExecutor(DOCKER_COMMAND, settings.getImage());
    }

    private static SandboxExecutor unsafeLocal() {
        LOG.warning("UNSAFE sandbox: player code runs on this machine (allow-unsafe=true, loopback only)");
        return new UnsafeLocalSandboxExecutor();
    }

    private static SandboxExecutor fake() {
        LOG.warning("FAKE sandbox (test profile): code is never run");
        return new FakeSandboxExecutor();
    }
}
