package dev.javarpg.runner.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.javarpg.runner.process.CommandRunner;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContainerReaperTest {

    /** Records commands and answers "ps" and "inspect" from canned text. */
    private static final class FakeCommands implements CommandRunner {
        final List<List<String>> quiet = new ArrayList<>();
        final String psOutput;
        final String inspectOutput;

        FakeCommands(String psOutput, String inspectOutput) {
            this.psOutput = psOutput;
            this.inspectOutput = inspectOutput;
        }

        @Override
        public String capture(List<String> command) {
            return command.contains("ps") ? psOutput : inspectOutput;
        }

        @Override
        public boolean runQuietly(List<String> command) {
            quiet.add(command);
            return true;
        }
    }

    @Test
    void removesOnlyLabelledJrContainersThatAreOldEnough() {
        String old = Instant.now().minusSeconds(600).toString();
        FakeCommands fake = new FakeCommands("aaa\nbbb\n", "aaa /jr-1 " + old + "\nbbb /other " + old + "\n");
        int removed = new ContainerReaper(fake, "docker").removeOlderThan(30L);
        assertEquals(1, removed);
        assertEquals(List.of("docker", "rm", "-f", "aaa"), fake.quiet.get(0));
    }

    @Test
    void nothingListedMeansNothingRemoved() {
        FakeCommands fake = new FakeCommands("\n", "");
        assertEquals(0, new ContainerReaper(fake, "docker").removeOlderThan(0L));
        assertTrue(fake.quiet.isEmpty());
    }

    @Test
    void killAndRemoveTargetTheRunsContainerName() {
        FakeCommands fake = new FakeCommands("", "");
        ContainerReaper reaper = new ContainerReaper(fake, "docker");
        reaper.kill("abc");
        reaper.remove("abc");
        assertEquals(List.of("docker", "kill", "jr-abc"), fake.quiet.get(0));
        assertEquals(List.of("docker", "rm", "-f", "jr-abc"), fake.quiet.get(1));
    }
}
