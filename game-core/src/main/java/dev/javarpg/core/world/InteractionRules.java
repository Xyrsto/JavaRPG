package dev.javarpg.core.world;

import java.util.Optional;

public final class InteractionRules {
    private InteractionRules() { }

    /** NPC at Manhattan distance 1 from the hero; if several, the one with the smallest id (String.compareTo). */
    public static Optional<NpcDef> adjacentNpc(WorldDef world, Point hero) {
        NpcDef best = null;
        for (NpcDef npc : world.npcs()) {
            int distance = Math.abs(npc.x() - hero.x()) + Math.abs(npc.y() - hero.y());
            if (distance == 1 && (best == null || npc.id().compareTo(best.id()) < 0)) {
                best = npc;
            }
        }
        return Optional.ofNullable(best);
    }
}
