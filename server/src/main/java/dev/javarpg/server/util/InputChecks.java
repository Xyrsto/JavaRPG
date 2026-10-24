package dev.javarpg.server.util;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.core.rules.Curriculum;
import java.util.regex.Pattern;

/** Boundary validation for path variables. Anything malformed is a friendly 400, never a stack trace. */
public final class InputChecks {

    private static final Pattern UUID_FORMAT = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private InputChecks() {
    }

    public static String requirePlayerId(String playerId) {
        if (playerId == null || !UUID_FORMAT.matcher(playerId).matches()) {
            throw new GameException(ErrorCode.BAD_REQUEST, "That player id is not valid.");
        }
        return playerId;
    }

    public static String requireQuestId(String questId) {
        if (questId == null || !Curriculum.isValidQuestId(questId)) {
            throw new GameException(ErrorCode.BAD_REQUEST, "That quest id is not valid.");
        }
        return questId;
    }
}
