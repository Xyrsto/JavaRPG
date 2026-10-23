package dev.javarpg.core.contract;

/** type is "OPEN_GATE" in this feature; target is a gate id from the world map ("A", "B"); message is player-facing. */
public record WorldEffect(String type, String target, String message) { }
