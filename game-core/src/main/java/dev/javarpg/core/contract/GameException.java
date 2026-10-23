package dev.javarpg.core.contract;

/** Expected, player-facing failure. message is friendly and safe to show. */
public class GameException extends RuntimeException {
    private final ErrorCode code;

    public GameException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
