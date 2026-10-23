package dev.javarpg.core.rules;

import dev.javarpg.core.contract.Profile;
import java.util.List;
import java.util.regex.Pattern;

/** Static curriculum data (Region table, boss flags, level ranges, expected profile per quest id). */
public final class Curriculum {
    private Curriculum() { }

    public record Region(String code, String name, int order, int questCount, int levelMin, int levelMax) { }

    private static final List<Region> REGIONS = List.of(
            new Region("V", "The Village of First Steps", 1, 10, 1, 5),
            new Region("F", "The Enchanted Forest of Objects", 2, 10, 6, 10),
            new Region("G", "The Forge of Inheritance", 3, 10, 11, 15),
            new Region("L", "The Arcane Library", 4, 12, 16, 21),
            new Region("D", "The Dungeon of Reliability", 5, 10, 22, 27),
            new Region("C", "The Citadel of Concurrency", 6, 10, 28, 33),
            new Region("A", "The Architect's Kingdom", 7, 12, 34, 40));

    private static final Pattern QUEST_ID_SHAPE = Pattern.compile("^[VFGLDCA]\\d{2}$");

    public static List<Region> regions() {
        return REGIONS;
    }

    public static Region regionOf(String questId) {
        return requireRegion(questId);
    }

    public static boolean isValidQuestId(String questId) {
        if (questId == null || !QUEST_ID_SHAPE.matcher(questId).matches()) {
            return false;
        }
        Region region = regionByCode(questId.substring(0, 1));
        int number = numberOf(questId);
        return region != null && number >= 1 && number <= region.questCount();
    }

    /** Position in the 74-quest curriculum, 0-based; throws IllegalArgumentException for an invalid id. */
    public static int globalIndex(String questId) {
        Region target = requireRegion(questId);
        int before = 0;
        for (Region region : REGIONS) {
            if (region.code().equals(target.code())) {
                break;
            }
            before += region.questCount();
        }
        return before + numberOf(questId) - 1;
    }

    public static boolean isBoss(String questId) {
        Region region = requireRegion(questId);
        return numberOf(questId) == region.questCount();
    }

    public static Profile expectedProfile(String questId) {
        Region region = requireRegion(questId);
        int number = numberOf(questId);
        return switch (region.code()) {
            case "D" -> {
                if (number == 5 || number == 6) {
                    yield Profile.FILES;
                }
                if (number == 7 || number == 9) {
                    yield Profile.BUILD;
                }
                yield Profile.TESTING;
            }
            case "C" -> Profile.THREADS;
            case "A" -> number <= 2 ? Profile.BASIC : Profile.SERVICE;
            default -> Profile.BASIC;
        };
    }

    private static Region requireRegion(String questId) {
        if (!isValidQuestId(questId)) {
            throw new IllegalArgumentException("Not a valid quest id: " + questId);
        }
        return regionByCode(questId.substring(0, 1));
    }

    private static Region regionByCode(String code) {
        for (Region region : REGIONS) {
            if (region.code().equals(code)) {
                return region;
            }
        }
        return null;
    }

    private static int numberOf(String questId) {
        return Integer.parseInt(questId.substring(1));
    }
}
