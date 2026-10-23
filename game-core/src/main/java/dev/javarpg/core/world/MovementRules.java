package dev.javarpg.core.world;

import java.util.List;
import java.util.Set;

public final class MovementRules {
    private MovementRules() { }

    /** True if (x,y) is outside the map, a wall, a closed gate (letter not in openGates) or an NPC tile. */
    public static boolean isBlocked(WorldDef world, Set<String> openGates, int x, int y) {
        TileMap map = world.map();
        if (x < 0 || y < 0 || x >= map.width() || y >= map.height()) {
            return true;
        }
        for (NpcDef npc : world.npcs()) {
            if (npc.x() == x && npc.y() == y) {
                return true;
            }
        }
        char tile = map.rows().get(y).charAt(x);
        if (tile == '#') {
            return true;
        }
        if (tile >= 'A' && tile <= 'Z') {
            return !openGates.contains(String.valueOf(tile));
        }
        return false;
    }

    /** Applies directions in order, stopping at the first blocked step. Pure. */
    public static MoveResult move(WorldDef world, Set<String> openGates, Point from, List<Direction> directions) {
        int x = from.x();
        int y = from.y();
        int applied = 0;
        for (Direction direction : directions) {
            int nextX = x + direction.dx();
            int nextY = y + direction.dy();
            if (isBlocked(world, openGates, nextX, nextY)) {
                return new MoveResult(new Point(x, y), applied, true);
            }
            x = nextX;
            y = nextY;
            applied++;
        }
        return new MoveResult(new Point(x, y), applied, false);
    }
}
