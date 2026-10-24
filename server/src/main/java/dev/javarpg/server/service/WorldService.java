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
import dev.javarpg.server.dto.ApiDtos.InteractResponse;
import dev.javarpg.server.dto.ApiDtos.MoveResponse;
import dev.javarpg.server.dto.ApiDtos.PlayerView;
import dev.javarpg.server.dto.ApiDtos.WorldView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Player-facing world use cases: the map, the player view, movement and NPC dialogue. */
@Service
public class WorldService {

    private final WorldDef world;
    private final QuestRepository repository;
    private final ProgressService progress;
    private final QuestAccess access;
    private final OpenGates openGates;
    private final WorldViewBuilder viewBuilder;

    public WorldService(WorldDef world, QuestRepository repository, ProgressService progress,
                        QuestAccess access, OpenGates openGates, WorldViewBuilder viewBuilder) {
        this.world = world;
        this.repository = repository;
        this.progress = progress;
        this.access = access;
        this.openGates = openGates;
        this.viewBuilder = viewBuilder;
    }

    public WorldView view() {
        return viewBuilder.build();
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
                p.completedQuestIds(), p.hero(), List.copyOf(openGates.forCompleted(completed)), effects);
    }

    public MoveResponse move(String playerId, List<Direction> directions) {
        PlayerProgress p = progress.getPlayerProgress(playerId);
        Set<String> gates = openGates.forCompleted(new HashSet<>(p.completedQuestIds()));
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
        QuestStatus status = access.statusOf(npc.questId(), new HashSet<>(p.completedQuestIds()));
        return new InteractResponse(npc.id(), npc.name(), npc.questId(), status.name(), linesFor(npc, status));
    }

    private static List<String> linesFor(NpcDef npc, QuestStatus status) {
        return switch (status) {
            case COMPLETED -> npc.completedLines();
            case LOCKED -> List.of("Come back after you finished the earlier quests.");
            case AVAILABLE -> npc.introLines();
        };
    }
}
