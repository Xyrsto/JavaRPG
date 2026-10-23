package dev.javarpg.core.world;

import java.util.List;

/** One-letter symbols in rows: '#' wall, '.' floor, 'A'..'Z' gate (blocked until opened). Row index = y, char index = x. */
public record TileMap(String id, int width, int height, List<String> rows) { }
