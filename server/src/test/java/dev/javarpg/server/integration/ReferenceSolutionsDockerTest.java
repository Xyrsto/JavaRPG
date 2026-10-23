package dev.javarpg.server.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.core.contract.GradeReport;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.TestScope;
import dev.javarpg.runner.docker.DockerSandboxExecutor;
import dev.javarpg.runner.grading.DefaultGrader;
import dev.javarpg.runner.grading.GradingPipeline;
import dev.javarpg.server.repository.FileQuestRepository;
import dev.javarpg.server.repository.WorldLoader;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Runs every shipped quest through the real Docker sandbox. Requires Docker and the javarpg-runner:1 image. */
@EnabledIfSystemProperty(named = "docker.tests", matches = "true")
class ReferenceSolutionsDockerTest {

    @Test
    void referenceSolutionsPassAndStarterCodeDoesNot() {
        FileQuestRepository repo = new FileQuestRepository(Path.of("../content/quests"),
                new WorldLoader().load(Path.of("../content/world/village.json")));
        GradingPipeline pipeline = new GradingPipeline(new DockerSandboxExecutor("docker", "javarpg-runner:1"),
                new DefaultGrader());
        for (Quest quest : repo.findAllOrdered()) {
            GradeReport reference = pipeline.evaluate(quest, TestScope.ALL, quest.referenceSolution(),
                    UUID.randomUUID().toString());
            assertTrue(reference.passed(), quest.id() + " reference solution failed: " + reference.summary());
            GradeReport starter = pipeline.evaluate(quest, TestScope.ALL, quest.starterCode(),
                    UUID.randomUUID().toString());
            assertFalse(starter.passed(), quest.id() + " starter code passed");
        }
    }
}
