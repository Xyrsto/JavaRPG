package dev.javarpg.core.contract;

/** Every error code the server can send. The HTTP status of each lives in the server module (ErrorStatus). */
public enum ErrorCode {
    BAD_REQUEST, PLAYER_NOT_FOUND, QUEST_NOT_FOUND, QUEST_LOCKED, QUEST_PROFILE_UNSUPPORTED,
    SOURCE_TOO_LARGE, HINT_NOT_AVAILABLE, NO_MORE_HINTS, RUN_IN_PROGRESS, SERVER_BUSY,
    NO_NPC_ADJACENT, INTERNAL,
    /** Unknown address. Written by the web layer, not by game rules. */
    NOT_FOUND,
    /** Request refused by the host check. Written by the web layer. */
    FORBIDDEN,
    /** Too many requests from one address. Written by the web layer. */
    RATE_LIMITED
}
