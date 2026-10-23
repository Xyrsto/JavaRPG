package dev.javarpg.server.config;

import dev.javarpg.core.contract.Evaluator;
import dev.javarpg.core.contract.Grader;
import dev.javarpg.core.contract.QuestRepository;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.runner.docker.DockerCleanup;
import dev.javarpg.runner.docker.DockerSandboxExecutor;
import dev.javarpg.runner.fake.FakeSandboxExecutor;
import dev.javarpg.runner.grading.DefaultGrader;
import dev.javarpg.runner.grading.GradingPipeline;
import dev.javarpg.runner.local.UnsafeLocalSandboxExecutor;
import dev.javarpg.server.repository.FileQuestRepository;
import dev.javarpg.server.repository.WorldLoader;
import dev.javarpg.server.service.RunGate;
import dev.javarpg.server.util.TokenBucketRateLimiter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Wires the shared beans. The sandbox is chosen by javarpg.sandbox.mode and fails fast on unknown values. */
@Configuration
@EnableConfigurationProperties(JavaRpgProperties.class)
public class SandboxConfig {

    private static final Logger LOG = Logger.getLogger(SandboxConfig.class.getName());

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public WorldDef worldDef(JavaRpgProperties props) {
        return new WorldLoader().load(Path.of(props.getWorldFile()));
    }

    @Bean
    public QuestRepository questRepository(JavaRpgProperties props, WorldDef worldDef) {
        return new FileQuestRepository(Path.of(props.getContentDir()), worldDef);
    }

    /** Security review F-05: removes sandbox containers older than 30 s (a crashed server leaves them behind). */
    private static void startReaper() {
        java.util.concurrent.ScheduledExecutorService reaper =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "sandbox-reaper");
                    thread.setDaemon(true);
                    return thread;
                });
        reaper.scheduleWithFixedDelay(() -> {
            try {
                int removed = DockerCleanup.removeStale("docker", 30L);
                if (removed > 0) {
                    LOG.info("Sandbox reaper removed " + removed + " stale runner containers");
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Sandbox reaper failed", e);
            }
        }, 60L, 60L, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Bean
    public SandboxExecutor sandboxExecutor(JavaRpgProperties props, Environment env) {
        String mode = props.getSandbox().getMode();
        SandboxSafety.requireStartAllowed(mode, props.getSandbox().isAllowUnsafe(),
                env.getProperty("server.address"), env.acceptsProfiles(Profiles.of("test")));
        if ("docker".equals(mode)) {
            int removed = DockerCleanup.removeOrphans("docker");
            LOG.info("Docker sandbox enabled; removed " + removed + " orphaned runner containers");
            startReaper();
            return new DockerSandboxExecutor("docker", props.getSandbox().getImage());
        }
        if ("unsafe-local".equals(mode)) {
            LOG.warning("UNSAFE sandbox: player code runs on this machine (allow-unsafe=true, loopback only)");
            return new UnsafeLocalSandboxExecutor();
        }
        if ("fake".equals(mode)) {
            LOG.warning("FAKE sandbox (test profile): code is never run");
            return new FakeSandboxExecutor();
        }
        throw new IllegalStateException("Unknown javarpg.sandbox.mode '" + mode
                + "'. Use docker, unsafe-local or fake.");
    }

    @Bean
    public Grader grader() {
        return new DefaultGrader();
    }

    @Bean
    public Evaluator evaluator(SandboxExecutor executor, Grader grader) {
        return new GradingPipeline(executor, grader);
    }

    @Bean
    public RunGate runGate(JavaRpgProperties props) {
        return new RunGate(props.getSandbox().getMaxConcurrent(), props.getSandbox().getAcquireWaitMillis());
    }

    @Bean
    public TokenBucketRateLimiter rateLimiter(Clock clock, JavaRpgProperties props) {
        return new TokenBucketRateLimiter(clock, props.getRate().getMaxTrackedClients());
    }
}
