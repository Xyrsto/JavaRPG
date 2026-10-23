package dev.javarpg.core.rules;

/** Maps total XP to a level using the thresholds of XpRules.xpFloorForLevel. Pure. */
public final class LevelRules {
    private LevelRules() { }

    public static final int MAX_LEVEL = 40;

    public static int levelForXp(int xp) {
        int level = 1;
        for (int candidate = 2; candidate <= MAX_LEVEL; candidate++) {
            if (XpRules.xpFloorForLevel(candidate) > xp) {
                break;
            }
            level = candidate;
        }
        return level;
    }

    /** XP needed for the next level, or 0 at MAX_LEVEL. */
    public static int nextLevelXp(int xp) {
        int level = levelForXp(xp);
        if (level >= MAX_LEVEL) {
            return 0;
        }
        return XpRules.xpFloorForLevel(level + 1);
    }
}
