package dev.javarpg.core.world;

import java.util.List;

/** NPC occupies tile (x,y) and blocks movement. questId is the quest it hands out. */
public record NpcDef(String id, String name, int x, int y, String questId, List<String> introLines, List<String> completedLines) { }
