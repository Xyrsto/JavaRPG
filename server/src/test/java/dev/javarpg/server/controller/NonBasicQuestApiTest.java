package dev.javarpg.server.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.server.support.TestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** A TESTING-profile quest (D01) is listed but never executed in this feature: the submit is refused with 422. */
@SpringBootTest(properties = "javarpg.content.dir=target/test-content-nonbasic")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NonBasicQuestApiTest {

    private static final Path CONTENT = Path.of("target/test-content-nonbasic");

    private static final String D01_JSON = """
            {
              "id": "D01",
              "region": "D",
              "title": "The Broken Bridge",
              "concept": "Debugging",
              "story": "A bridge lies in pieces.",
              "goal": "Print 42.",
              "kind": "STDOUT",
              "starterCode": ["public class Main {", "    public static void main(String[] args) {", "        // TODO", "    }", "}"],
              "profile": "TESTING",
              "visibleTests": [{"name": "prints the number", "expected": "42", "match": "EXACT_TRIMMED"}],
              "hiddenTests": [{"name": "prints the number again", "expected": "42", "match": "EXACT_TRIMMED"}],
              "hints": ["First hint", "Second hint", "Third hint"],
              "referenceSolution": ["public class Main {", "    public static void main(String[] args) {", "        System.out.println(42);", "    }", "}"],
              "explanation": "println prints a value.",
              "commonMistakes": [],
              "worldEffect": null
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SandboxExecutor executor;

    @BeforeAll
    static void writeNonBasicContent() throws Exception {
        Files.createDirectories(CONTENT);
        Files.writeString(CONTENT.resolve("D01.json"), D01_JSON);
    }

    @Test
    void aNonBasicQuestIsRefusedWith422AndNeverRun() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TestSupport.program(executor, request -> {
            calls.incrementAndGet();
            return TestSupport.ok("42\n");
        });
        String playerId = TestSupport.createPlayer(mvc);
        TestSupport.submit(mvc, playerId, "D01", "public class Main {}\n")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("QUEST_PROFILE_UNSUPPORTED"));
        assertEquals(0, calls.get());
    }
}
