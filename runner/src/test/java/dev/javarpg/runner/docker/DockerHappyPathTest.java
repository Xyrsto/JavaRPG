package dev.javarpg.runner.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "docker.tests", matches = "true")
class DockerHappyPathTest {

    private static final String HELLO = "public class Main {\n"
            + "    public static void main(String[] args) {\n"
            + "        System.out.println(\"Hello, Adventurer!\");\n"
            + "    }\n"
            + "}\n";

    private final DockerSandboxExecutor executor = new DockerSandboxExecutor("docker", "javarpg-runner:1");

    @Test
    void helloWorldRunsOk() {
        SandboxResult r = executor.execute(new SandboxRequest(UUID.randomUUID().toString(),
                Profile.BASIC, Map.of("Main.java", HELLO), "Main"));
        assertEquals(SandboxStatus.OK, r.status());
        assertEquals("Hello, Adventurer!", r.stdout().trim());
    }

    @Test
    void compileErrorIsReturnedVerbatim() {
        String broken = "public class Main {\n    public static void main(String[] args) {\n        int x = ;\n    }\n}\n";
        SandboxResult r = executor.execute(new SandboxRequest(UUID.randomUUID().toString(),
                Profile.BASIC, Map.of("Main.java", broken), "Main"));
        assertEquals(SandboxStatus.COMPILE_ERROR, r.status());
        assertTrue(r.compilerOutput().contains("Main.java:"));
    }
}
