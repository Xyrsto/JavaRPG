package dev.javarpg.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** The documented keys must bind; javarpg.world.file once silently fell back to the default. */
class JavaRpgPropertiesBindingTest {

    private static JavaRpgProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("javarpg", JavaRpgProperties.class)
                .orElseGet(JavaRpgProperties::new);
    }

    @Test
    void pathKeysBindToTheirGroups() {
        JavaRpgProperties props = bind(Map.of(
                "javarpg.content.dir", "../content/quests",
                "javarpg.world.file", "../content/world/village.json",
                "javarpg.data.dir", "./target/test-data"));
        assertEquals("../content/quests", props.getContent().getDir());
        assertEquals("../content/world/village.json", props.getWorld().getFile());
        assertEquals("./target/test-data", props.getData().getDir());
    }

    @Test
    void defaultsApplyWhenNothingIsSet() {
        JavaRpgProperties props = bind(Map.of());
        assertEquals("content/quests", props.getContent().getDir());
        assertEquals("content/world/village.json", props.getWorld().getFile());
        assertEquals("./data", props.getData().getDir());
    }

    @Test
    void otherGroupsStillBind() {
        JavaRpgProperties props = bind(Map.of(
                "javarpg.sandbox.mode", "fake",
                "javarpg.sandbox.acquire-wait-millis", "5000",
                "javarpg.rate.players-per-minute", "7",
                "javarpg.http.max-body-bytes", "100",
                "javarpg.players.idle-days", "3"));
        assertEquals("fake", props.getSandbox().getMode());
        assertEquals(5000L, props.getSandbox().getAcquireWaitMillis());
        assertEquals(7, props.getRate().getPlayersPerMinute());
        assertEquals(100L, props.getHttp().getMaxBodyBytes());
        assertEquals(3, props.getPlayers().getIdleDays());
    }
}
