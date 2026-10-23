package dev.javarpg.server.service;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.contract.PlayerProgress;
import dev.javarpg.core.contract.ProgressService;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.QuestRepository;
import dev.javarpg.core.contract.WorldEffect;
import dev.javarpg.core.world.Direction;
import dev.javarpg.core.world.InteractionRules;
import dev.javarpg.core.world.MoveResult;
import dev.javarpg.core.world.MovementRules;
import dev.javarpg.core.world.NpcDef;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.server.dto.ApiDtos.GateView;
import dev.javarpg.server.dto.ApiDtos.InteractResponse;
import dev.javarpg.server.dto.ApiDtos.MoveResponse;
import dev.javarpg.server.dto.ApiDtos.NpcView;
import dev.javarpg.server.dto.ApiDtos.PlayerView;
import dev.javarpg.server.dto.ApiDtos.WorldView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/** World map, movement, NPC dialogue and the player view. Gate state is derived from completed quests. */
@Service
public class WorldService {

    private final WorldDef world;
    private final QuestRepository repository;
    private final ProgressService progress;
    private final QuestViewService quests;

    public WorldService(WorldDef world, QuestRepository repository, ProgressService progress,
                        QuestViewService quests) {
        this.world = world;
        this.repository = repository;
        this.progress = progress;
        this.quests = quests;
    }

    public WorldView view() {
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

    public PlayerView playerView(String playerId) {
        PlayerProgress p = progress.getPlayerProgress(playerId);
        Set<String> completed = new HashSet<>(p.completedQuestIds());
        List<WorldEffect> effects = new ArrayList<>();
        for (Quest quest : repository.findAllOrdered()) {
            if (completed.contains(quest.id()) && quest.worldEffect() != null) {
                effects.add(quest.worldEffect());
            }
        }
        return new PlayerView(p.playerId(), p.xp(), p.level(), p.levelFloorXp(), p.nextLevelXp(),
                p.completedQuestIds(), p.hero(), List.copyOf(openGates(completed)), effects);
    }

    /** Gate ids opened by completed quests whose worldEffect is OPEN_GATE, sorted. */
    public Set<String> openGates(Set<String> completed) {
        Set<String> gates = new TreeSet<>();
        for (Quest quest : repository.findAllOrdered()) {
            WorldEffect effect = quest.worldEffect();
            if (effect != null && "OPEN_GATE".equals(effect.type()) && completed.contains(quest.id())) {
                gates.add(effect.target());
            }
        }
        return gates;
    }

    public MoveResponse move(String playerId, List<Direction> directions) {
        PlayerProgress p = progress.getPlayerProgress(playerId);
        Set<String> gates = openGates(new HashSet<>(p.completedQuestIds()));
        MoveResult result = MovementRules.move(world, gates, p.hero(), directions);
        progress.saveHeroPosition(playerId, result.position());
        return new MoveResponse(result.position().x(), result.position().y(), result.applied(), result.blocked());
    }

    public InteractResponse interact(String playerId) {
        PlayerProgress p = progress.getPlayerProgress(playerId);
        Optional<NpcDef> adjacent = InteractionRules.adjacentNpc(world, p.hero());
        if (adjacent.isEmpty()) {
            throw new GameException(ErrorCode.NO_NPC_ADJACENT, "There is nobody to talk to here.");
        }
        NpcDef npc = adjacent.get();
        String status = quests.statusOf(npc.questId(), new HashSet<>(p.completedQuestIds()));
        List<String> lines;
        if ("COMPLETED".equals(status)) {
            lines = npc.completedLines();
        } else if ("LOCKED".equals(status)) {
            lines = List.of("Come back after you finished the earlier quests.");
        } else {
            lines = npc.introLines();
        }
        return new InteractResponse(npc.id(), npc.name(), npc.questId(), status, lines);
    }
}
