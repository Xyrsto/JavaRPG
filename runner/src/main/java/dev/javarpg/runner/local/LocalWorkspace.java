package dev.javarpg.runner.local;

import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.runner.docker.DockerCommandBuilder;
import dev.javarpg.runner.process.Directories;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** A throwaway directory holding one run's sources and compiled classes. Closing it deletes everything. */
final class LocalWorkspace implements AutoCloseable {

    private final Path dir;
    private final Path classes;

    private LocalWorkspace(Path dir, Path classes) {
        this.dir = dir;
        this.classes = classes;
    }

    static LocalWorkspace create(SandboxRequest request) throws IOException {
        DockerCommandBuilder.checkSourceNames(request.sources());
        Path dir = Files.createTempDirectory("jr-local-");
        try {
            for (Map.Entry<String, String> entry : request.sources().entrySet()) {
                Files.writeString(dir.resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
            }
            return new LocalWorkspace(dir, Files.createDirectory(dir.resolve("classes")));
        } catch (IOException | RuntimeException e) {
            Directories.deleteRecursively(dir);
            throw e;
        }
    }

    Path dir() {
        return dir;
    }

    Path classes() {
        return classes;
    }

    @Override
    public void close() {
        Directories.deleteRecursively(dir);
    }
}
