package dev.javarpg.runner.docker;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Confirms once that the sandbox image exists locally; later calls return immediately. */
final class DockerImageCheck {

    private final String dockerBinary;
    private final String image;
    private final Object lock = new Object();
    private boolean ready;

    DockerImageCheck(String dockerBinary, String image) {
        this.dockerBinary = dockerBinary;
        this.image = image;
    }

    void ensureReady() throws IOException, InterruptedException {
        synchronized (lock) {
            if (ready) {
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
            ready = true;
        }
    }
}
