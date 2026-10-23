package dev.javarpg.runner.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** {@link CommandRunner} backed by real child processes, each limited to 10 seconds. */
public final class SystemCommandRunner implements CommandRunner {

    private static final Logger LOG = Logger.getLogger(SystemCommandRunner.class.getName());
    private static final long TIMEOUT_SECONDS = 10;

    @Override
    public String capture(List<String> command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
        }
        return out;
    }

    @Override
    public boolean runQuietly(List<String> command) {
        try {
            Process p = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            p.getOutputStream().close();
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "A docker command could not run", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
