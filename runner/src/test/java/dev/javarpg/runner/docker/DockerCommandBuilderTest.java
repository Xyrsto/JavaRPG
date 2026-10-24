package dev.javarpg.runner.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.SandboxRequest;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DockerCommandBuilderTest {

    private static final String RUN_ID = "11111111-2222-3333-4444-555555555555";
    private static final String IMAGE = "javarpg-runner:1";

    private static SandboxRequest request(Profile profile, String runId, Map<String, String> sources, String mainClass) {
        return new SandboxRequest(runId, profile, sources, mainClass);
    }

    @Test
    void basicProfileGivesExactArgumentList() {
        Path dir = Paths.get("sandbox-input").toAbsolutePath();
        List<String> args = DockerCommandBuilder.build(
                request(Profile.BASIC, RUN_ID, Map.of("Main.java", "x"), "Main"),
                dir, LimitProfile.forProfile(Profile.BASIC), IMAGE);
        List<String> expected = List.of(
                "run", "--rm", "--name", "jr-" + RUN_ID,
                "--label", "javarpg.sandbox=1", "--pull", "never", "--init", "--log-driver", "none",
                "--network", "none", "--read-only", "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--pids-limit", "64", "--memory", "384m", "--memory-swap", "384m", "--cpus", "1",
                "--user", "65534:65534", "--ulimit", "nofile=256:256",
                "--ulimit", "fsize=33554432", "--ulimit", "core=0",
                "--tmpfs", "/work:rw,noexec,nosuid,size=32m,mode=1777",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=16m,mode=1777",
                "--mount", "type=bind,src=" + dir + ",dst=/in,readonly",
                "-e", "JR_MAIN=Main", "-e", "JR_RUN_TIMEOUT=3",
                IMAGE);
        assertEquals(expected, args);
    }

    @Test
    void forbiddenFlagsAreAbsent() {
        Path dir = Paths.get("sandbox-input").toAbsolutePath();
        List<String> args = DockerCommandBuilder.build(
                request(Profile.BASIC, RUN_ID, Map.of("Main.java", "x"), "Main"),
                dir, LimitProfile.forProfile(Profile.BASIC), IMAGE);
        for (String forbidden : List.of("--privileged", "-i", "-t", "-p", "host", "--cap-add")) {
            assertFalse(args.contains(forbidden), forbidden);
        }
        // No -v / --volume at all: the only host mount is the one --mount, and it is read-only.
        assertFalse(args.contains("-v"));
        assertFalse(args.contains("--volume"));
        assertEquals(1L, args.stream().filter("--mount"::equals).count());
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).startsWith("-v") || args.get(i).startsWith("--volume")) {
                throw new AssertionError("writable-capable mount flag: " + args.get(i));
            }
            if (args.get(i).startsWith("type=bind")) {
                assertTrue(args.get(i).endsWith(",dst=/in,readonly"), args.get(i));
                assertEquals("--mount", args.get(i - 1));
            }
        }
        assertEquals(IMAGE, args.get(args.size() - 1));
    }

    @Test
    void everyHardeningFlagFromTheSecurityReviewIsPresent() {
        Path dir = Paths.get("sandbox-input").toAbsolutePath();
        List<String> args = DockerCommandBuilder.build(
                request(Profile.BASIC, RUN_ID, Map.of("Main.java", "x"), "Main"),
                dir, LimitProfile.forProfile(Profile.BASIC), IMAGE);
        assertFlagValue(args, "--label", "javarpg.sandbox=1");
        assertFlagValue(args, "--log-driver", "none");
        assertFlagValue(args, "--pull", "never");
        assertFlagValue(args, "--ulimit", "fsize=33554432");
        assertFlagValue(args, "--ulimit", "core=0");
        assertFlagValue(args, "--ulimit", "nofile=256:256");
        assertFlagValue(args, "--network", "none");
        assertFlagValue(args, "--cap-drop", "ALL");
        assertFlagValue(args, "--security-opt", "no-new-privileges");
        assertTrue(args.contains("--init"));
        assertTrue(args.contains("--read-only"));
    }

    private static void assertFlagValue(List<String> args, String flag, String value) {
        for (int i = 0; i < args.size() - 1; i++) {
            if (args.get(i).equals(flag) && args.get(i + 1).equals(value)) {
                return;
            }
        }
        throw new AssertionError("Missing " + flag + " " + value + " in " + args);
    }

    @Test
    void inputDirectoryPathWithMountSeparatorsIsRejected() {
        for (String bad : List.of("a,b", "a\"b")) {
            assertThrows(IllegalArgumentException.class, () -> DockerCommandBuilder.build(
                    request(Profile.BASIC, RUN_ID, Map.of("Main.java", "x"), "Main"),
                    Paths.get(bad), LimitProfile.forProfile(Profile.BASIC), IMAGE), bad);
        }
    }

    @Test
    void unimplementedProfileIsRejectedWithQuestProfileUnsupported() {
        Path dir = Paths.get("sandbox-input").toAbsolutePath();
        GameException ex = assertThrows(GameException.class, () -> DockerCommandBuilder.build(
                request(Profile.TESTING, RUN_ID, Map.of("Main.java", "x"), "Main"),
                dir, LimitProfile.forProfile(Profile.TESTING), IMAGE));
        assertEquals(ErrorCode.QUEST_PROFILE_UNSUPPORTED, ex.code());
    }

    @Test
    void illegalRunIdIsRejected() {
        Path dir = Paths.get("sandbox-input").toAbsolutePath();
        assertThrows(IllegalArgumentException.class, () -> DockerCommandBuilder.build(
                request(Profile.BASIC, "bad id; rm -rf", Map.of("Main.java", "x"), "Main"),
                dir, LimitProfile.forProfile(Profile.BASIC), IMAGE));
    }

    @Test
    void illegalSourceNamesAreRejected() {
        Path dir = Paths.get("sandbox-input").toAbsolutePath();
        for (String name : List.of("../evil.java", "Main.txt", "1Main.java")) {
            assertThrows(IllegalArgumentException.class, () -> DockerCommandBuilder.build(
                    request(Profile.BASIC, RUN_ID, Map.of(name, "x"), "Main"),
                    dir, LimitProfile.forProfile(Profile.BASIC), IMAGE), name);
        }
    }

    @Test
    void illegalMainClassIsRejected() {
        Path dir = Paths.get("sandbox-input").toAbsolutePath();
        assertThrows(IllegalArgumentException.class, () -> DockerCommandBuilder.build(
                request(Profile.BASIC, RUN_ID, Map.of("Main.java", "x"), "Main; rm"),
                dir, LimitProfile.forProfile(Profile.BASIC), IMAGE));
    }
}
