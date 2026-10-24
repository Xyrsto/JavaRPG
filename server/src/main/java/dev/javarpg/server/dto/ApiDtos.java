package dev.javarpg.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.javarpg.core.contract.WorldEffect;
import dev.javarpg.core.world.Point;
import java.util.List;
import java.util.Map;

/**
 * JSON shapes of plan section 5. Component names are the JSON keys.
 *
 * Accessibility and keyboard mapping (T252): every success and error response carries a plain-text
 * summary or message; directions are the semantic enum UP/DOWN/LEFT/RIGHT, so arrow keys and WASD both
 * map to them; the Interact key calls /interact, Run calls /run, Submit calls /submit and Hint calls
 * /hints/next. Nothing relies on colour or field order to carry meaning.
 *
 * Hidden test data, reference solutions and explanations are never placed in a shape here unless the
 * quest is COMPLETED (explanation, referenceSolution and worldEffect on the detail view).
 */
public final class ApiDtos {

    private ApiDtos() {
    }

    public record PlayerView(String playerId, int xp, int level, int levelFloorXp, int nextLevelXp,
                             List<String> completedQuestIds, Point hero, List<String> openGates,
                             List<WorldEffect> worldEffects) {
    }

    public record WorldView(String id, int width, int height, List<String> rows, Map<String, String> legend,
                            Point heroStart, List<GateView> gates, List<NpcView> npcs) {
    }

    public record GateView(String id, int x, int y) {
    }

    public record NpcView(String id, String name, int x, int y, String questId) {
    }

    /** At most 32 directions (same bound as WorldController); longer lists fail deserialisation with a 400. */
    public record MoveRequest(List<String> directions) {
        public static final int MAX_DIRECTIONS = 32;

        public MoveRequest {
            if (directions != null && directions.size() > MAX_DIRECTIONS) {
                throw new IllegalArgumentException("too many directions");
            }
        }
    }

    public record MoveResponse(int x, int y, int applied, boolean blocked) {
    }

    public record InteractResponse(String npcId, String npcName, String questId, String questStatus,
                                   List<String> lines) {
    }

    public record QuestListResponse(List<QuestSummary> quests) {
    }

    public record QuestSummary(String id, String region, String regionName, String title, String concept,
                               String status, int xp) {
    }

    /** expected is null for SOURCE_REGEX tests (their name carries the human text). */
    public record VisibleTestView(String name, String call, String expected) {
    }

    public record QuestDetailView(String id, String title, String concept, String story, String goal,
                                  String kind, String profile, String status, int xp, String starterCode,
                                  String lastSource, List<VisibleTestView> visibleTests, int hintsTotal,
                                  int hintsUnlocked, List<String> hints, int failedAttempts,
                                  String explanation, String referenceSolution, WorldEffect worldEffect) {
    }

    public record SourceRequest(String source) {
    }

    public record TestResultView(String name, boolean hidden, boolean passed, String expected, String actual,
                                 String message) {
    }

    public record RunResponse(String questId, String scope, boolean passed, String status,
                              String compilerOutput, String stdout, String stderr, String summary,
                              List<String> friendlyHints, List<TestResultView> tests, long durationMillis) {
    }

    public record SubmitResponse(String questId, String scope, boolean passed, String status,
                                 String compilerOutput, String stdout, String stderr, String summary,
                                 List<String> friendlyHints, List<TestResultView> tests, long durationMillis,
                                 CompletionView completion) {
    }

    /** Present only when a Submit passed; null otherwise. */
    public record CompletionView(boolean firstCompletion, int xpAwarded, int totalXp, int level, boolean levelUp,
                                 WorldEffect worldEffect, String explanation, String unlockedQuestId) {
    }

    public record HintResponse(int hintNumber, String hint, int hintsUnlocked, int hintsTotal) {
    }

    public record ErrorBody(String code, String message) {
    }

    public record ErrorEnvelope(ErrorBody error) {
    }

    /** warning is present only when the sandbox mode is not docker. */
    public record HealthView(String status, String sandbox, boolean sandboxSafe, int questsLoaded,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String warning) {
    }
}
