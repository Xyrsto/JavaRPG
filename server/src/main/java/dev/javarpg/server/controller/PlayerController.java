package dev.javarpg.server.controller;

import dev.javarpg.core.contract.ProgressService;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.server.dto.ApiDtos.PlayerView;
import dev.javarpg.server.service.PlayerLimits;
import dev.javarpg.server.service.WorldService;
import dev.javarpg.server.util.InputChecks;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PlayerController {

    private final ProgressService progress;
    private final WorldService worlds;
    private final WorldDef world;
    private final PlayerLimits limits;

    public PlayerController(ProgressService progress, WorldService worlds, WorldDef world, PlayerLimits limits) {
        this.progress = progress;
        this.worlds = worlds;
        this.world = world;
        this.limits = limits;
    }

    @PostMapping("/players")
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerView create() {
        // A player id is the only credential (no accounts): anyone holding a leaked UUID controls that
        // player. Accepted for a local single-user game (security review F-15).
        String playerId = limits.createWithinCap(() -> progress.createPlayer(world.heroStart()));
        return worlds.playerView(playerId);
    }

    @GetMapping("/players/{playerId}")
    public PlayerView get(@PathVariable String playerId) {
        InputChecks.requirePlayerId(playerId);
        return worlds.playerView(playerId);
    }
}
