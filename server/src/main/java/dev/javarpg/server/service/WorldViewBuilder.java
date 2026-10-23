package dev.javarpg.server.service;

import dev.javarpg.core.world.NpcDef;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.server.dto.ApiDtos.GateView;
import dev.javarpg.server.dto.ApiDtos.NpcView;
import dev.javarpg.server.dto.ApiDtos.WorldView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds the static world description (map, legend, gates, NPCs) that the client draws. */
@Component
public class WorldViewBuilder {

    private final WorldDef world;

    public WorldViewBuilder(WorldDef world) {
        this.world = world;
    }

    public WorldView build() {
        Map<String, String> legend = new LinkedHashMap<>();
        legend.put("#", "wall");
        legend.put(".", "floor");
        List<GateView> gates = new ArrayList<>();
        List<String> rows = world.map().rows();
        for (int y = 0; y < rows.size(); y++) {
            String row = rows.get(y);
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                if (c >= 'A' && c <= 'Z') {
                    gates.add(new GateView(String.valueOf(c), x, y));
                    legend.put(String.valueOf(c), "gate");
                }
            }
        }
        List<NpcView> npcs = new ArrayList<>();
        for (NpcDef npc : world.npcs()) {
            npcs.add(new NpcView(npc.id(), npc.name(), npc.x(), npc.y(), npc.questId()));
        }
        return new WorldView(world.map().id(), world.map().width(), world.map().height(), rows, legend,
                world.heroStart(), gates, npcs);
    }
}
