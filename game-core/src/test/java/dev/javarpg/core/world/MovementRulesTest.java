package dev.javarpg.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MovementRulesTest {

    // 5x5 room: walls on the border, gate A in the bottom wall at (2,4), NPC at (1,1).
    private static final List<String> ROWS = List.of(
            "#####",
            "#...#",
            "#...#",
            "#...#",
            "##A##");

    private static final WorldDef WORLD = new WorldDef(
            new TileMap("test", 5, 5, ROWS),
            new Point(2, 2),
            List.of(new NpcDef("npc", "Tester", 1, 1, "V01", List.of("hi"), List.of("bye"))));

    private static final Set<String> CLOSED = Set.of();
    private static final Set<String> GATE_A_OPEN = Set.of("A");

    @Test
    void wallStopsMovement() {
        MoveResult result = MovementRules.move(WORLD, CLOSED, new Point(2, 2),
                List.of(Direction.LEFT, Direction.LEFT, Direction.LEFT));
        assertEquals(new Point(1, 2), result.position());
        assertEquals(1, result.applied());
        assertTrue(result.blocked());
    }

    @Test
    void mapBoundsStopMovement() {
        MoveResult result = MovementRules.move(WORLD, GATE_A_OPEN, new Point(2, 4),
                List.of(Direction.DOWN));
        assertEquals(new Point(2, 4), result.position());
        assertEquals(0, result.applied());
        assertTrue(result.blocked());
        assertTrue(MovementRules.isBlocked(WORLD, CLOSED, -1, 2));
        assertTrue(MovementRules.isBlocked(WORLD, CLOSED, 5, 2));
    }

    @Test
    void npcTileStopsMovement() {
        MoveResult result = MovementRules.move(WORLD, CLOSED, new Point(1, 2),
                List.of(Direction.UP));
        assertEquals(new Point(1, 2), result.position());
        assertEquals(0, result.applied());
        assertTrue(result.blocked());
    }

    @Test
    void closedGateBlocksOpenGateAllows() {
        MoveResult closed = MovementRules.move(WORLD, CLOSED, new Point(2, 3),
                List.of(Direction.DOWN));
        assertEquals(new Point(2, 3), closed.position());
        assertEquals(0, closed.applied());
        assertTrue(closed.blocked());

        MoveResult open = MovementRules.move(WORLD, GATE_A_OPEN, new Point(2, 3),
                List.of(Direction.DOWN));
        assertEquals(new Point(2, 4), open.position());
        assertEquals(1, open.applied());
        assertFalse(open.blocked());
    }

    @Test
    void emptyDirectionListStaysPut() {
        MoveResult result = MovementRules.move(WORLD, CLOSED, new Point(2, 2), List.of());
        assertEquals(new Point(2, 2), result.position());
        assertEquals(0, result.applied());
        assertFalse(result.blocked());
    }

    @Test
    void appliedCountsOnlyWalkedSteps() {
        MoveResult result = MovementRules.move(WORLD, CLOSED, new Point(1, 3),
                List.of(Direction.RIGHT, Direction.RIGHT, Direction.RIGHT));
        assertEquals(new Point(3, 3), result.position());
        assertEquals(2, result.applied());
        assertTrue(result.blocked());
    }

    @Test
    void movementIsPure() {
        List<Direction> path = List.of(Direction.RIGHT, Direction.UP);
        MoveResult first = MovementRules.move(WORLD, GATE_A_OPEN, new Point(1, 3), path);
        MoveResult second = MovementRules.move(WORLD, GATE_A_OPEN, new Point(1, 3), path);
        assertEquals(first, second);
    }
}
