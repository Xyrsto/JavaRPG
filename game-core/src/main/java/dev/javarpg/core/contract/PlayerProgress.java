package dev.javarpg.core.contract;

import dev.javarpg.core.world.Point;
import java.util.List;

/**
 * level/levelFloorXp/nextLevelXp come from LevelRules. nextLevelXp is 0 at max level.
 * completedQuestIds are in completion-independent curriculum order.
 */
public record PlayerProgress(
        String playerId,
        int xp,
        int level,
        int levelFloorXp,
        int nextLevelXp,
        List<String> completedQuestIds,
        Point hero) { }
