package dev.javarpg.server.controller;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.world.Direction;
import dev.javarpg.server.dto.ApiDtos.InteractResponse;
import dev.javarpg.server.dto.ApiDtos.MoveRequest;
import dev.javarpg.server.dto.ApiDtos.MoveResponse;
import dev.javarpg.server.dto.ApiDtos.WorldView;
import dev.javarpg.server.service.WorldService;
import dev.javarpg.server.util.InputChecks;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorldController {

    private static final int MAX_DIRECTIONS = 32;

    private final WorldService worlds;

    public WorldController(WorldService worlds) {
        this.worlds = worlds;
    }

    @GetMapping("/world")
    public WorldView world() {
        return worlds.view();
    }

    @PostMapping("/players/{playerId}/move")
    public MoveResponse move(@PathVariable String playerId, @RequestBody MoveRequest body) {
        InputChecks.requirePlayerId(playerId);
        List<String> names = body.directions();
        if (names == null || names.isEmpty() || names.size() > MAX_DIRECTIONS) {
            throw new GameException(ErrorCode.BAD_REQUEST, "Send between 1 and 32 directions: UP, DOWN, LEFT or RIGHT.");
        }
        List<Direction> directions = new ArrayList<>();
        for (String name : names) {
            directions.add(parseDirection(name));
        }
        return worlds.move(playerId, directions);
    }

    @PostMapping("/players/{playerId}/interact")
    public InteractResponse interact(@PathVariable String playerId) {
        InputChecks.requirePlayerId(playerId);
        return worlds.interact(playerId);
    }

    private static Direction parseDirection(String name) {
        if (name != null) {
            try {
                return Direction.valueOf(name);
            } catch (IllegalArgumentException e) {
                // fall through to the friendly error below
            }
        }
        throw new GameException(ErrorCode.BAD_REQUEST, "Unknown direction. Use UP, DOWN, LEFT or RIGHT.");
    }
}
