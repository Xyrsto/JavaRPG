package dev.javarpg.runner.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DockerCleanupTest {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    @Test
    void onlyOldEnoughJrContainersAreSelected() {
        String inspect = String.join("\n",
                "aaa /jr-11111111 2026-10-23T11:59:00.123456789Z",
                "bbb /jr-22222222 2026-10-23T11:59:50.000000000Z",
                "ccc /my-jr-db 2026-10-23T10:00:00Z",
                "ddd /jr-33333333 not-a-date",
                "");
        assertEquals(List.of("aaa"), DockerCleanup.selectRemovable(inspect, NOW, 30L));
    }

    @Test
    void zeroAgeSelectsEveryJrContainerButNeverOtherNames() {
        String inspect = "aaa /jr-1 2026-10-23T11:59:59Z\nccc /cjr-cache 2026-10-23T11:00:00Z\nddd /jr-db 2026-10-23T11:00:00Z\n";
        assertEquals(List.of("aaa", "ddd"), DockerCleanup.selectRemovable(inspect, NOW, 0L));
    }

    @Test
    void emptyOrGarbageOutputSelectsNothing() {
        assertEquals(List.of(), DockerCleanup.selectRemovable("", NOW, 30L));
        assertEquals(List.of(), DockerCleanup.selectRemovable("Error: No such object\n", NOW, 30L));
    }
}
