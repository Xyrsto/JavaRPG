package dev.javarpg.core.world;

import java.util.List;

/** applied = directions actually walked (stops at first blocked step). blocked = true if a step was refused. */
public record MoveResult(Point position, int applied, boolean blocked) { }
