package dev.javarpg.runner.local;

import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.runner.docker.DockerCleanup;
import dev.javarpg.runner.docker.DockerCommandBuilder;
import dev.javarpg.runner.docker.ExitCodeInterpreter;
import dev.javarpg.runner.harness.HarnessProtocol;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UNSAFE: no isolation, development only. Player code runs as a child JVM on this machine with
 * the same rights as the server process. Enabled only by javarpg.sandbox.mode=unsafe-local.
 */
public final class UnsafeLocalSandboxExecutor implements SandboxExecutor {

    private static final Logger LOG = Logger.getLogger(UnsafeLocalSandboxExecutor.class.getName());
    private static final long COMPILE_TIMEOUT_MILLIS = 15_000L;
    private static final long RUN_TIMEOUT_MILLIS = 3_000L;
    private static final int STDOUT_CAP = 64 * 1024;
    private static final int STDERR_CAP = 16 * 1024;
    private static final String NOT_AVAILABLE = "The code runner is not available right now.";

    private record Captured(int exit, String stdout, String stderr, boolean truncated, boolean timedOut, long millis) {
    }

    @Override
    public SandboxResult execute(SandboxRequest request) {
        long start = System.nanoTime();
        Path dir = null;
        try {
            if (request.profile() != Profile.BASIC) {
                return error(start, "The unsafe local runner supports BASIC challenges only.");
            }
            DockerCommandBuilder.checkSourceNames(request.sources());
            dir = Files.createTempDirectory("jr-local-");
            for (Map.Entry<String, String> entry : request.sources().entrySet()) {
                Files.writeString(dir.resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
            }
            Path classes = Files.createDirectory(dir.resolve("classes"));

            // Same two-step compile as docker/entrypoint.sh (F-10): player files alone, then the harness.
            List<String> playerFiles = new ArrayList<>();
            boolean hasHarness = false;
            for (String name : request.sources().keySet()) {
                if (HarnessProtocol.HARNESS_FILE.equals(name)) {
                    hasHarness = true;
                } else {
                    playerFiles.add(name);
                }
            }
            Captured built = run(javac(classes, null, playerFiles), dir, COMPILE_TIMEOUT_MILLIS, true);
            if (built.timedOut()) {
                return compileTimeout(built, start);
            }
            if (built.exit() != 0) {
                return new SandboxResult(SandboxStatus.COMPILE_ERROR, built.exit(), built.stdout(), "", "",
                        built.truncated(), millisSince(start));
            }
            if (hasHarness) {
                Captured harness = run(javac(classes, classes, List.of(HarnessProtocol.HARNESS_FILE)), dir,
                        COMPILE_TIMEOUT_MILLIS, true);
                if (harness.timedOut()) {
                    return compileTimeout(harness, start);
                }
                if (harness.exit() != 0) {
                    return new SandboxResult(SandboxStatus.COMPILE_ERROR, harness.exit(),
                            HarnessProtocol.HARNESS_COMPILE_MESSAGE, "", "", false, millisSince(start));
                }
            }

            List<String> runCommand = List.of(javaTool("java"), "-Xmx96m", "-Xss512k",
                    "-XX:+UseSerialGC", "-XX:-UsePerfData", "-Dfile.encoding=UTF-8",
                    "-cp", classes.toString(), request.mainClass());
            Captured ran = run(runCommand, dir, RUN_TIMEOUT_MILLIS, false);
            SandboxStatus status = ExitCodeInterpreter.interpret(ran.exit(), ran.millis(), RUN_TIMEOUT_MILLIS,
                    ran.stderr(), ran.truncated(), ran.timedOut());
            return new SandboxResult(status, ran.exit(), "", ran.stdout(), ran.stderr(),
                    ran.truncated(), millisSince(start));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Unsafe local run failed", e);
            return error(start, NOT_AVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "Unsafe local run interrupted", e);
            return error(start, NOT_AVAILABLE);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Unsafe local run error", e);
            return error(start, NOT_AVAILABLE);
        } finally {
            DockerCleanup.deleteRecursively(dir);
        }
    }

    private static List<String> javac(Path classes, Path classpath, List<String> files) {
        List<String> command = new ArrayList<>();
        command.add(javaTool("javac"));
        command.add("-encoding");
        command.add("UTF-8");
        command.add("-proc:none");
        if (classpath != null) {
            command.add("-cp");
            command.add(classpath.toString());
        }
        command.add("-d");
        command.add(classes.toString());
        command.addAll(files);
        return command;
    }

    private static SandboxResult compileTimeout(Captured step, long start) {
        return new SandboxResult(SandboxStatus.TIMEOUT, step.exit(), "", "",
                HarnessProtocol.COMPILE_TIMEOUT_MESSAGE, step.truncated(), millisSince(start));
    }

    private static Captured run(List<String> command, Path cwd, long timeoutMillis, boolean mergeErrors)
            throws IOException, InterruptedException {
        long start = System.nanoTime();
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(mergeErrors)
                .start();
        process.getOutputStream().close();
        Capture out = new Capture(process.getInputStream(), STDOUT_CAP);
        Capture err = mergeErrors ? null : new Capture(process.getErrorStream(), STDERR_CAP);
        out.start();
        if (err != null) {
            err.start();
        }
        boolean timedOut = !process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        if (timedOut) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        out.join(2000);
        if (err != null) {
            err.join(2000);
        }
        int exit = process.isAlive() ? -1 : process.exitValue();
        return new Captured(exit, out.text(), err == null ? "" : err.text(), out.truncated(), timedOut,
                millisSince(start));
    }

    private static String javaTool(String name) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String exe = os.contains("win") ? name + ".exe" : name;
        return Paths.get(System.getProperty("java.home"), "bin", exe).toString();
    }

    private static SandboxResult error(long start, String message) {
        return new SandboxResult(SandboxStatus.SANDBOX_ERROR, -1, "", "", message, false, millisSince(start));
    }

    private static long millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }

    /** Bounded reader: keeps the first cap bytes, drains and discards the rest. */
    private static final class Capture extends Thread {
        private final InputStream in;
        private final int cap;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final Object lock = new Object();
        private boolean truncated;

        Capture(InputStream in, int cap) {
            super("jr-local-reader");
            this.in = in;
            this.cap = cap;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] chunk = new byte[4096];
            try (InputStream source = in) {
                int n;
                while ((n = source.read(chunk)) != -1) {
                    synchronized (lock) {
                        int room = cap - buffer.size();
                        if (n <= room) {
                            buffer.write(chunk, 0, n);
                        } else {
                            if (room > 0) {
                                buffer.write(chunk, 0, room);
                            }
                            truncated = true;
                        }
                    }
                }
            } catch (IOException e) {
                LOG.log(Level.FINE, "Local sandbox stream closed", e);
            }
        }

        String text() {
            synchronized (lock) {
                return buffer.toString(StandardCharsets.UTF_8);
            }
        }

        boolean truncated() {
            synchronized (lock) {
                return truncated;
            }
        }
    }
}
