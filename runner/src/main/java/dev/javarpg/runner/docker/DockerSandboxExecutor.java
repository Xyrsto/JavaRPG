package dev.javarpg.runner.docker;

import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.runner.process.Directories;
import dev.javarpg.runner.process.Elapsed;
import dev.javarpg.runner.process.SystemCommandRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs player code in a disposable Docker container. Never throws for player faults.
 *
 * <p>Run base directory (F-04): see {@link RunWorkspace}. Set it with the system property
 * {@value #BASE_DIR_PROPERTY} or the three-argument constructor. Without it the platform temp directory
 * is used.
 */
public final class DockerSandboxExecutor implements SandboxExecutor {

    /** Optional system property: server-owned (mode 700) directory that holds the per-run directories. */
    public static final String BASE_DIR_PROPERTY = "javarpg.run.baseDir";

    private static final Logger LOG = Logger.getLogger(DockerSandboxExecutor.class.getName());

    private final String image;
    private final Path baseDir;
    private final DockerImageCheck imageCheck;
    private final ContainerRunner containerRunner;

    public DockerSandboxExecutor(String dockerBinary, String image) {
        this(dockerBinary, image, baseDirFromProperty());
    }

    /** baseDir may be null (use the platform temp directory). */
    public DockerSandboxExecutor(String dockerBinary, String image, Path baseDir) {
        this.image = image;
        this.baseDir = baseDir;
        this.imageCheck = new DockerImageCheck(dockerBinary, image);
        this.containerRunner = new ContainerRunner(dockerBinary,
                new ContainerReaper(new SystemCommandRunner(), dockerBinary));
    }

    private static Path baseDirFromProperty() {
        String value = System.getProperty(BASE_DIR_PROPERTY);
        return value == null || value.isBlank() ? null : Path.of(value.trim());
    }

    @Override
    public SandboxResult execute(SandboxRequest request) {
        long start = System.nanoTime();
        Path dir = null;
        try {
            imageCheck.ensureReady();
            LimitProfile limits = LimitProfile.forProfile(request.profile());
            dir = RunWorkspace.create(request, baseDir);
            List<String> args = DockerCommandBuilder.build(request, dir, limits, image);
            ContainerOutcome outcome = containerRunner.run(request.runId(), args, start);
            return ContainerResultMapper.map(outcome, limits);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "Sandbox run interrupted", e);
            return ContainerResultMapper.sandboxError(Elapsed.millisSince(start));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Sandbox run failed", e);
            return ContainerResultMapper.sandboxError(Elapsed.millisSince(start));
        } finally {
            Directories.deleteRecursively(dir);
        }
    }
}
