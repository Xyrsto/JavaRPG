package dev.javarpg.runner.docker;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.contract.SandboxRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds the argument list for "docker run" (plan 6.2). The caller prepends the docker binary.
 * No shell is involved; every element is a separate argument.
 */
public final class DockerCommandBuilder {

    /** Label on every sandbox container; cleanup and tests filter on it, never on a name (F-06). */
    public static final String SANDBOX_LABEL = "javarpg.sandbox=1";

    private static final Pattern RUN_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Pattern SOURCE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*\\.java");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern IMAGE = Pattern.compile("[a-z0-9][a-z0-9._/-]*(:[A-Za-z0-9._-]+)?");

    private DockerCommandBuilder() {
    }

    public static List<String> build(SandboxRequest request, Path hostInputDir, LimitProfile limits, String image) {
        if (request == null || hostInputDir == null || limits == null) {
            throw new IllegalArgumentException("Missing sandbox argument.");
        }
        if (!limits.implemented()) {
            throw new GameException(ErrorCode.QUEST_PROFILE_UNSUPPORTED, "This kind of challenge is not available yet.");
        }
        if (limits.network()) {
            throw new IllegalStateException("Network access is never allowed in the sandbox.");
        }
        if (request.profile() != limits.profile()) {
            throw new IllegalArgumentException("Request profile does not match its limit profile.");
        }
        if (request.runId() == null || !RUN_ID.matcher(request.runId()).matches()) {
            throw new IllegalArgumentException("Run id has illegal characters.");
        }
        checkSourceNames(request.sources());
        if (request.mainClass() == null || !IDENTIFIER.matcher(request.mainClass()).matches()) {
            throw new IllegalArgumentException("Main class name is not valid.");
        }
        if (image == null || !IMAGE.matcher(image).matches()) {
            throw new IllegalArgumentException("Image name is not valid.");
        }
        String hostPath = hostInputDir.toAbsolutePath().toString();
        if (hostPath.indexOf(',') >= 0 || hostPath.indexOf('"') >= 0 || hostPath.indexOf('\n') >= 0
                || hostPath.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Input directory path has characters that are not allowed.");
        }
        String memory = limits.memoryMb() + "m";
        List<String> args = new ArrayList<>();
        args.add("run");
        args.add("--rm");
        args.add("--name");
        args.add("jr-" + request.runId());
        args.add("--label");
        args.add(SANDBOX_LABEL);
        args.add("--pull");
        args.add("never");
        args.add("--init");
        args.add("--log-driver");
        args.add("none");
        args.add("--network");
        args.add("none");
        args.add("--read-only");
        args.add("--cap-drop");
        args.add("ALL");
        args.add("--security-opt");
        args.add("no-new-privileges");
        args.add("--pids-limit");
        args.add(Integer.toString(limits.pids()));
        args.add("--memory");
        args.add(memory);
        args.add("--memory-swap");
        args.add(memory);
        args.add("--cpus");
        args.add(formatCpus(limits.cpus()));
        args.add("--user");
        args.add("65534:65534");
        args.add("--ulimit");
        args.add("nofile=256:256");
        args.add("--ulimit");
        args.add("fsize=33554432");
        args.add("--ulimit");
        args.add("core=0");
        args.add("--tmpfs");
        args.add("/work:rw,noexec,nosuid,size=32m,mode=1777");
        args.add("--tmpfs");
        args.add("/tmp:rw,noexec,nosuid,size=16m,mode=1777");
        // --mount fails closed if the directory vanished (-v would create it as root) and has no ":" ambiguity.
        args.add("--mount");
        args.add("type=bind,src=" + hostPath + ",dst=/in,readonly");
        args.add("-e");
        args.add("JR_MAIN=" + request.mainClass());
        args.add("-e");
        args.add("JR_RUN_TIMEOUT=" + limits.runTimeoutSeconds());
        args.add(image);
        return List.copyOf(args);
    }

    /** Only plain "Name.java" files reach the sandbox directory. */
    public static void checkSourceNames(Map<String, String> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("No source files to run.");
        }
        for (String name : sources.keySet()) {
            if (name == null || !SOURCE_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Illegal source file name: " + name);
            }
        }
    }

    static String formatCpus(double cpus) {
        return cpus == Math.rint(cpus) ? Long.toString((long) cpus) : Double.toString(cpus);
    }
}
