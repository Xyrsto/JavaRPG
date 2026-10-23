package dev.javarpg.server.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import dev.javarpg.core.world.Point;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.server.exception.ContentLoadException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldLoaderTest {

    private static final Path VILLAGE = Path.of("../content/world/village.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dir;

    @Test
    void loadsTheVillage() {
        WorldDef world = new WorldLoader().load(VILLAGE);
        assertEquals(12, world.map().width());
        assertEquals(9, world.map().height());
        assertEquals(2, world.npcs().size());
        assertEquals(new Point(5, 4), world.heroStart());
    }

    @Test
    void rejectsAWrongRowCount() throws Exception {
        ObjectNode root = readVillage();
        ((ArrayNode) root.get("rows")).remove(8);
        assertRejected(root);
    }

    @Test
    void rejectsAWrongRowLength() throws Exception {
        ObjectNode root = readVillage();
        ((ArrayNode) root.get("rows")).set(1, TextNode.valueOf("#...........#"));
        assertRejected(root);
    }

    @Test
    void rejectsAHeroStandingOnAWall() throws Exception {
        ObjectNode root = readVillage();
        ((ObjectNode) root.get("heroStart")).put("x", 0).put("y", 0);
        assertRejected(root);
    }

    @Test
    void rejectsAnNpcStandingOnAWall() throws Exception {
        ObjectNode root = readVillage();
        ((ObjectNode) root.get("npcs").get(0)).put("x", 0).put("y", 0);
        assertRejected(root);
    }

    @Test
    void rejectsAnInvalidQuestId() throws Exception {
        ObjectNode root = readVillage();
        ((ObjectNode) root.get("npcs").get(0)).put("questId", "X99");
        assertRejected(root);
    }

    private ObjectNode readVillage() throws Exception {
        JsonNode node = JSON.readTree(Files.readString(VILLAGE));
        return (ObjectNode) node;
    }

    private void assertRejected(ObjectNode root) throws Exception {
        Path file = dir.resolve("bad.json");
        Files.writeString(file, JSON.writeValueAsString(root));
        ContentLoadException e = assertThrows(ContentLoadException.class, () -> new WorldLoader().load(file));
        assertTrue(e.getMessage().contains("bad.json"), e.getMessage());
    }
}
