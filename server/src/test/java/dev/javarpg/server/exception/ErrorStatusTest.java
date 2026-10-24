package dev.javarpg.server.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.javarpg.core.contract.ErrorCode;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** FR-001, FR-002: every error code has exactly one HTTP status, and these are the ones clients have always seen. */
class ErrorStatusTest {

    private static final Map<ErrorCode, HttpStatus> GOLDEN = new EnumMap<>(ErrorCode.class);

    static {
        GOLDEN.put(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        GOLDEN.put(ErrorCode.PLAYER_NOT_FOUND, HttpStatus.NOT_FOUND);
        GOLDEN.put(ErrorCode.QUEST_NOT_FOUND, HttpStatus.NOT_FOUND);
        GOLDEN.put(ErrorCode.QUEST_LOCKED, HttpStatus.FORBIDDEN);
        GOLDEN.put(ErrorCode.QUEST_PROFILE_UNSUPPORTED, HttpStatus.UNPROCESSABLE_ENTITY);
        GOLDEN.put(ErrorCode.SOURCE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE);
        GOLDEN.put(ErrorCode.HINT_NOT_AVAILABLE, HttpStatus.CONFLICT);
        GOLDEN.put(ErrorCode.NO_MORE_HINTS, HttpStatus.CONFLICT);
        GOLDEN.put(ErrorCode.RUN_IN_PROGRESS, HttpStatus.TOO_MANY_REQUESTS);
        GOLDEN.put(ErrorCode.SERVER_BUSY, HttpStatus.SERVICE_UNAVAILABLE);
        GOLDEN.put(ErrorCode.NO_NPC_ADJACENT, HttpStatus.CONFLICT);
        GOLDEN.put(ErrorCode.INTERNAL, HttpStatus.INTERNAL_SERVER_ERROR);
        GOLDEN.put(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND);
        GOLDEN.put(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        GOLDEN.put(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void theGoldenTableCoversEveryCode() {
        assertEquals(15, ErrorCode.values().length);
        assertEquals(GOLDEN.keySet(), java.util.Set.copyOf(java.util.List.of(ErrorCode.values())));
    }

    @Test
    void everyCodeMapsToTheStatusClientsAlreadySee() {
        for (ErrorCode code : ErrorCode.values()) {
            assertEquals(GOLDEN.get(code), ErrorStatus.of(code), code.name());
        }
    }
}
