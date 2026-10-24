package dev.javarpg.server.exception;

import dev.javarpg.core.contract.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * The one place that says which HTTP status goes with which error code. The switch has no default branch, so
 * adding an ErrorCode without a status here does not compile. The HTTP status is a web concern and so lives in
 * the server module, not in the game rules.
 */
public final class ErrorStatus {

    private ErrorStatus() {
    }

    public static HttpStatus of(ErrorCode code) {
        return switch (code) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case PLAYER_NOT_FOUND, QUEST_NOT_FOUND, NOT_FOUND -> HttpStatus.NOT_FOUND;
            case QUEST_LOCKED, FORBIDDEN -> HttpStatus.FORBIDDEN;
            case QUEST_PROFILE_UNSUPPORTED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case SOURCE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case HINT_NOT_AVAILABLE, NO_MORE_HINTS, NO_NPC_ADJACENT -> HttpStatus.CONFLICT;
            case RUN_IN_PROGRESS, RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case SERVER_BUSY -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
