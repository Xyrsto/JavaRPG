package dev.javarpg.server.service;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;

/** Boundary rules for submitted player source (never trust the client): not empty, not oversized. */
final class SourcePolicy {

    static final int MAX_SOURCE_CHARS = 20000;

    private SourcePolicy() {
    }

    static void require(String source) {
        if (source == null || source.isBlank()) {
            throw new GameException(ErrorCode.BAD_REQUEST, "Your code is empty. Write some Java first.");
        }
        if (source.length() > MAX_SOURCE_CHARS) {
            throw new GameException(ErrorCode.SOURCE_TOO_LARGE,
                    "That code is too long. Keep it under " + MAX_SOURCE_CHARS + " characters.");
        }
    }
}
