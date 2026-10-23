package dev.javarpg.runner.docker;

import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.runner.harness.HarnessProtocol;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs player code in a disposable Docker container. Never throws for player faults.
 *
 * <p>Run base directory (F-04): per-run source directories are world-readable (755) so that uid 65534 in
 * the container can read them. The base directory above them should therefore be owned by the server user
 * with mode 700, for example /var/lib/javarpg/run. Set it with the system property
 * {@value #BASE_DIR_PROPERTY} or the three-argument constructor; it is created with owner-only permissions
 * where the OS supports POSIX permissions. Without it the platform temp directory is used.
 */
public final class DockerSandboxExecutor implements SandboxExecutor {

    /** Optional system property: server-owned (mode 700) directory that holds the per-run directories. */
    public static final String BASE_DIR_PROPERTY = "javarpg.run.baseDir";

    static final long WATCHDOG_MILLIS = 15_000L;
    static final int STDOUT_CAP = 64 * 1024;
    static final int STDERR_CAP = 16 * 1024;

    private static final String NOT_AVAILABLE = "The code runner is not available right now.";
    private static final Logger LOG = Logger.getLogger(DockerSandboxExecutor.class.getName());
    private static final Set<PosixFilePermission> DIR_PERMS = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE);
    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMS = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ);

    private final String dockerBinary;
    private final String image;
    private final Path baseDir;
    private final Object imageLock = new Object();
    private boolean imageReady;

    public DockerSandboxExecutor(String dockerBinary, String image) {
        this(dockerBinary, image, baseDirFromProperty());
    }

    /** baseDir may be null (use the platform temp directory). */
    public DockerSandboxExecutor(String dockerBinary, String image, Path baseDir) {
        this.dockerBinary = dockerBinary;
        this.image = image;
        this.baseDir = baseDir;
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
            checkImage();
            LimitProfile limits = LimitProfile.forProfile(request.profile());
            dir = writeSources(request, baseDir);
            List<String> args = DockerCommandBuilder.build(request, dir, limits, image);
            return runContainer(request.runId(), args, limits, start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "Sandbox run interrupted", e);
            return sandboxError(start);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Sandbox run failed", e);
            return sandboxError(start);
        } finally {
            DockerCleanup.deleteRecursively(dir);
        }
    }

    private void checkImage() throws IOException, InterruptedException {
        synchronized (imageLock) {
            if (imageReady) {
                return;
            }
            Process p = new ProcessBuilder(dockerBinary, "image", "inspect", image)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            p.getOutputStream().close();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("docker image inspect timed out");
            }
            if (p.exitValue() != 0) {
                throw new IOException("Image " + image + " is missing. Build it with: docker build -t "
                        + image + " runner/docker");
            }
            imageReady = true;
        }
    }

    /** Creates the run base directory with owner-only permissions where supported (F-04). */
    private static void ensureBaseDir(Path base) throws IOException {
        if (Files.isDirectory(base)) {
            return;
        }
        try {
            Files.createDirectories(base, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
        } catch (UnsupportedOperationException e) {
            Files.createDirectories(base);
        }
    }

    static Path writeSources(SandboxRequest request, Path base) throws IOException {
        DockerCommandBuilder.checkSourceNames(request.sources());
        Path dir;
        if (base == null) {
            dir = Files.createTempDirectory("jr-");
        } else {
            ensureBaseDir(base);
            dir = Files.createTempDirectory(base, "jr-");
        }
        try {
            makeReadable(dir, DIR_PERMS);
            for (Map.Entry<String, String> entry : request.sources().entrySet()) {
                Path file = dir.resolve(entry.getKey());
                Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
                makeReadable(file, FILE_PERMS);
            }
            return dir;
        } catch (IOException | RuntimeException e) {
            DockerCleanup.deleteRecursively(dir);
            throw e;
        }
    }

    /** Container user 65534 must read the mounted sources. Windows has no POSIX modes, so skip there. */
    private static void makeReadable(Path path, Set<PosixFilePermission> perms) throws IOException {
        try {
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException e) {
            LOG.log(Level.FINE, "No POSIX permissions on this platform: " + path);
        }
    }

    private SandboxResult runContainer(String runId, List<String> args, LimitProfile limits, long start)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(dockerBinary);
        command.addAll(args);
        Process process = new ProcessBuilder(command).start();
        process.getOutputStream().close();
        CappedReader stdout = new CappedReader(process.getInputStream(), STDOUT_CAP,
                () -> DockerCleanup.killContainer(dockerBinary, runId));
        CappedReader stderr = new CappedReader(process.getErrorStream(), STDERR_CAP, null);
        stdout.start();
        stderr.start();
        try {
            boolean watchdogFired = false;
            if (!process.waitFor(WATCHDOG_MILLIS, TimeUnit.MILLISECONDS)) {
                watchdogFired = true;
                DockerCleanup.killContainer(dockerBinary, runId);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            stdout.join(2000);
            stderr.join(2000);
            int exit = process.isAlive() ? -1 : process.exitValue();
            String out = stdout.text();
            String err = stderr.text();
            boolean limitHit = stdout.limitHit();
            long duration = millisSince(start);
            // F-03: Docker CLI/daemon failures carry host paths and versions. Log them, return a generic error.
            if (!watchdogFired && isDockerFailure(exit, err)) {
                LOG.warning("docker run failed (exit " + exit + "): " + err);
                return sandboxError(start);
            }
            if (exit == ExitCodeInterpreter.COMPILE_TIMED_OUT && !watchdogFired && !limitHit) {
                return new SandboxResult(SandboxStatus.TIMEOUT, exit, "", "",
                        HarnessProtocol.COMPILE_TIMEOUT_MESSAGE, false, duration);
            }
            SandboxStatus status = ExitCodeInterpreter.interpret(exit, duration,
                    limits.runTimeoutSeconds() * 1000L, err, limitHit, watchdogFired);
            boolean compile = status == SandboxStatus.COMPILE_ERROR;
            return new SandboxResult(status, exit, compile ? out : "", compile ? "" : out, err,
                    limitHit, duration);
        } finally {
            DockerCleanup.removeContainer(dockerBinary, runId);
        }
    }

    static boolean isDockerFailure(int exit, String stderr) {
        String trimmed = stderr == null ? "" : stderr.stripLeading();
        return exit == 125 || exit == 126 || exit == 127
                || trimmed.startsWith("docker:") || trimmed.startsWith("Error response from daemon");
    }

    private static SandboxResult sandboxError(long start) {
        return new SandboxResult(SandboxStatus.SANDBOX_ERROR, -1, "", "", NOT_AVAILABLE, false, millisSince(start));
    }

    private static long millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }

    /** Reads a stream into a bounded buffer. Past the cap it keeps draining but discards the bytes. */
    private static final class CappedReader extends Thread {
        private final InputStream in;
        private final int cap;
        private final Runnable onOverflow;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final Object lock = new Object();
        private boolean limitHit;

        CappedReader(InputStream in, int cap, Runnable onOverflow) {
            super("jr-sandbox-reader");
            this.in = in;
            this.cap = cap;
            this.onOverflow = onOverflow;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] chunk = new byte[4096];
            try (InputStream source = in) {
                int n;
                while ((n = source.read(chunk)) != -1) {
                    boolean fire = false;
                    synchronized (lock) {
                        int room = cap - buffer.size();
                        if (n <= room) {
                            buffer.write(chunk, 0, n);
                        } else {
                            if (room > 0) {
                                buffer.write(chunk, 0, room);
                            }
                            fire = !limitHit;
                            limitHit = true;
                        }
                    }
                    if (fire && onOverflow != null) {
                        onOverflow.run();
                    }
                }
            } catch (IOException e) {
                // The pipe closes when the process ends or is killed; there is nothing more to read.
                LOG.log(Level.FINE, "Sandbox stream closed", e);
            }
        }

        String text() {
            synchronized (lock) {
                return buffer.toString(StandardCharsets.UTF_8);
            }
        }

        boolean limitHit() {
            synchronized (lock) {
                return limitHit;
            }
        }
    }
}
