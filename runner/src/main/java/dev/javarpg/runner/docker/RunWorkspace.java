package dev.javarpg.runner.docker;

import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.runner.process.Directories;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The per-run source directory that is mounted into the container.
 *
 * <p>Run base directory (F-04): per-run directories are world-readable (755) so that uid 65534 in the
 * container can read them. The base directory above them should therefore be owned by the server user with
 * mode 700; it is created with owner-only permissions where the OS supports POSIX permissions.
 */
final class RunWorkspace {

    private static final Logger LOG = Logger.getLogger(RunWorkspace.class.getName());
    private static final Set<PosixFilePermission> DIR_PERMS = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE);
    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMS = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ);

    private RunWorkspace() {
    }

    /** Writes the request's sources into a fresh directory under base (null: platform temp directory). */
    static Path create(SandboxRequest request, Path base) throws IOException {
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
            Directories.deleteRecursively(dir);
            throw e;
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

    /** Container user 65534 must read the mounted sources. Windows has no POSIX modes, so skip there. */
    private static void makeReadable(Path path, Set<PosixFilePermission> perms) throws IOException {
        try {
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException e) {
            LOG.log(Level.FINE, "No POSIX permissions on this platform: " + path);
        }
    }
}
