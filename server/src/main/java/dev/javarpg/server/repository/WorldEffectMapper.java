package dev.javarpg.server.repository;

import static dev.javarpg.server.repository.ContentChecks.check;
import static dev.javarpg.server.repository.ContentChecks.nonBlank;

import dev.javarpg.core.contract.WorldEffect;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.server.dto.QuestFileDto.WorldEffectFileDto;

/** Validates the optional worldEffect of a quest file against the loaded map. */
final class WorldEffectMapper {

    private final WorldDef world;

    WorldEffectMapper(WorldDef world) {
        this.world = world;
    }

    /** Null in, null out: most quests have no world effect. */
    WorldEffect map(String file, WorldEffectFileDto dto) {
        if (dto == null) {
            return null;
        }
        check(file, "OPEN_GATE".equals(dto.type()), "worldEffect.type", "must be OPEN_GATE");
        check(file, isGateOnMap(dto.target()), "worldEffect.target", "must name a gate on the map");
        check(file, nonBlank(dto.message()), "worldEffect.message", "must not be blank");
        return new WorldEffect(dto.type(), dto.target(), dto.message());
    }

    private boolean isGateOnMap(String target) {
        if (target == null || target.length() != 1) {
            return false;
        }
        char letter = target.charAt(0);
        if (letter < 'A' || letter > 'Z') {
            return false;
        }
        for (String row : world.map().rows()) {
            if (row.indexOf(letter) >= 0) {
                return true;
            }
        }
        return false;
    }
}
