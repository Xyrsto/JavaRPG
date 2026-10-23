package dev.javarpg.core.rules;

/** XP awarded per quest and the XP needed per level (region formula of plan 3.6). */
public final class XpRules {
    private XpRules() { }

    public static final int NORMAL_QUEST_XP = 100;
    public static final int BOSS_QUEST_XP = 250;

    /** 250 for a boss quest, otherwise 100. */
    public static int xpFor(String questId) {
        return Curriculum.isBoss(questId) ? BOSS_QUEST_XP : NORMAL_QUEST_XP;
    }

    /** XP needed to be at `level` (1..40). level 1 -> 0. */
    public static int xpFloorForLevel(int level) {
        if (level < 1 || level > LevelRules.MAX_LEVEL) {
            throw new IllegalArgumentException("Level out of range: " + level);
        }
        int regionStartXp = 0;
        for (Curriculum.Region region : Curriculum.regions()) {
            if (level <= region.levelMax()) {
                int levelsInRegion = region.levelMax() - region.levelMin() + 1;
                int stepsIntoRegion = level - region.levelMin();
                int regionXp = regionXp(region);
                return regionStartXp + (regionXp * stepsIntoRegion + levelsInRegion - 1) / levelsInRegion;
            }
            regionStartXp += regionXp(region);
        }
        throw new IllegalStateException("No region covers level " + level);
    }

    private static int regionXp(Curriculum.Region region) {
        return (region.questCount() - 1) * NORMAL_QUEST_XP + BOSS_QUEST_XP;
    }
}
