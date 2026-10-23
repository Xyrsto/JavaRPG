package dev.javarpg.core.world;

import java.util.List;

public record WorldDef(TileMap map, Point heroStart, List<NpcDef> npcs) { }
