package dev.javarpg.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InteractionRulesTest {

    private static WorldDef worldWith(NpcDef... npcs) {
        return new WorldDef(new TileMap("test", 12, 9, List.of()), new Point(5, 4), List.of(npcs));
    }

    private static NpcDef npcAt(String id, int x, int y) {
        return new NpcDef(id, id, x, y, "V01", List.of(), List.of());
    }

    @Test
    void npcInEachOrthogonalDirectionIsFound() {
        Point hero = new Point(5, 4);
        assertEquals("right", InteractionRules.adjacentNpc(worldWith(npcAt("right", 6, 4)), hero).orElseThrow().id());
        assertEquals("left", InteractionRules.adjacentNpc(worldWith(npcAt("left", 4, 4)), hero).orElseThrow().id());
        assertEquals("down", InteractionRules.adjacentNpc(worldWith(npcAt("down", 5, 5)), hero).orElseThrow().id());
        assertEquals("up", InteractionRules.adjacentNpc(worldWith(npcAt("up", 5, 3)), hero).orElseThrow().id());
    }

    @Test
    void diagonalNpcIsNotFound() {
        assertTrue(InteractionRules.adjacentNpc(worldWith(npcAt("diag", 6, 5)), new Point(5, 4)).isEmpty());
    }

    @Test
    void noNpcReturnsEmpty() {
        assertTrue(InteractionRules.adjacentNpc(worldWith(), new Point(5, 4)).isEmpty());
    }

    @Test
    void twoAdjacentNpcsReturnSmallestId() {
        NpcDef picked = InteractionRules.adjacentNpc(
                worldWith(npcAt("zed", 6, 4), npcAt("ann", 5, 5)), new Point(5, 4)).orElseThrow();
        assertEquals("ann", picked.id());
    }
}
