package dev.javarpg.runner.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.SandboxRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DockerSandboxExecutorTest {

    @Test
    void dockerCliAndDaemonFailuresAreRecognised() {
        assertTrue(ContainerResultMapper.isDockerFailure(125, ""));
        assertTrue(ContainerResultMapper.isDockerFailure(126, ""));
        assertTrue(ContainerResultMapper.isDockerFailure(127, ""));
        assertTrue(ContainerResultMapper.isDockerFailure(1, "docker: Error response from daemon: x"));
        assertTrue(ContainerResultMapper.isDockerFailure(1, "  Error response from daemon: no such image"));
        assertFalse(ContainerResultMapper.isDockerFailure(1, "Exception in thread \"main\" java.lang.Error"));
        assertFalse(ContainerResultMapper.isDockerFailure(0, ""));
        assertFalse(ContainerResultMapper.isDockerFailure(124, ""));
    }

    @Test
    void runBaseDirIsCreatedOwnerOnlyAndHoldsTheRunDirectory(@TempDir Path tmp) throws IOException {
        Path base = tmp.resolve("run");
        SandboxRequest request = new SandboxRequest("11111111-2222-3333-4444-555555555555", Profile.BASIC,
                Map.of("Main.java", "public class Main { }"), "Main");
        Path dir = RunWorkspace.create(request, base);
        try {
            assertEquals(base, dir.getParent());
            assertTrue(Files.exists(dir.resolve("Main.java")));
            if (Files.getFileStore(base).supportsFileAttributeView("posix")) {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(base);
                assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE), perms);
            }
        } finally {
            DockerCleanup.deleteRecursively(dir);
        }
    }
}
