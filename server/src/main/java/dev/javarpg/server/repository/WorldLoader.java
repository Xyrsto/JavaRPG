package dev.javarpg.server.repository;

import static dev.javarpg.server.repository.ContentChecks.check;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.javarpg.core.rules.Curriculum;
import dev.javarpg.core.world.NpcDef;
import dev.javarpg.core.world.Point;
import dev.javarpg.core.world.TileMap;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.server.exception.ContentLoadException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads and validates the world map file (plan 3.6). */
public final class WorldLoader {

    private static final char WALL = '#';
    private static final char FLOOR = '.';
    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    /** Raw shape of the world JSON file. */
    public record WorldFile(String id, int width, int height, List<String> rows, Point heroStart, List<NpcDef> npcs) { }

    public WorldDef load(Path file) {
        String name = file.getFileName().toString();
        WorldFile raw;
        try {
            raw = MAPPER.readValue(Files.readString(file), WorldFile.class);
        } catch (IOException | RuntimeException e) {
            throw new ContentLoadException(name + ": cannot read the world file: " + e.getMessage(), e);
        }

        check(name, raw.id() != null && !raw.id().isBlank(), "id", "must not be blank");
        check(name, raw.width() > 0 && raw.height() > 0, "width", "and 'height' must be positive");
        check(name, raw.rows() != null && raw.rows().size() == raw.height(), "rows",
                "must contain exactly " + raw.height() + " rows");
        for (int y = 0; y < raw.rows().size(); y++) {
            String row = raw.rows().get(y);
            check(name, row != null && row.length() == raw.width(), "rows",
                    "row " + y + " must be exactly " + raw.width() + " characters");
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                boolean gate = c >= 'A' && c <= 'Z';
                check(name, c == WALL || c == FLOOR || gate, "rows",
                        "row " + y + " has an unknown symbol '" + c + "'");
            }
        }
        checkGatesAreSingleTiles(name, raw.rows());

        check(name, raw.heroStart() != null && isFloor(raw, raw.heroStart().x(), raw.heroStart().y()),
                "heroStart", "must be a floor tile inside the map");

        List<NpcDef> npcs = raw.npcs() == null ? List.of() : raw.npcs();
        Set<String> npcIds = new HashSet<>();
        for (NpcDef npc : npcs) {
            check(name, npc.id() != null && npcIds.add(npc.id()), "npcs", "each npc needs a unique id");
            check(name, isFloor(raw, npc.x(), npc.y()), "npcs",
                    "npc '" + npc.id() + "' must stand on a floor tile inside the map");
            check(name, Curriculum.isValidQuestId(npc.questId()), "questId",
                    "of npc '" + npc.id() + "' must be a valid quest id");
        }

        return new WorldDef(new TileMap(raw.id(), raw.width(), raw.height(), List.copyOf(raw.rows())),
                raw.heroStart(), List.copyOf(npcs));
    }

    private static void checkGatesAreSingleTiles(String name, List<String> rows) {
        Set<Character> seen = new HashSet<>();
        for (String row : rows) {
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                if (c >= 'A' && c <= 'Z') {
                    check(name, seen.add(c), "rows", "gate '" + c + "' must occupy exactly one tile");
                }
            }
        }
    }

    private static boolean isFloor(WorldFile raw, int x, int y) {
        if (x < 0 || y < 0 || y >= raw.rows().size()) {
            return false;
        }
        String row = raw.rows().get(y);
        return x < row.length() && row.charAt(x) == FLOOR;
    }
}
