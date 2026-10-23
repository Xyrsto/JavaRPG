package dev.javarpg.core.contract;

/**
 * Full pipeline used by the server: source guard -> harness generation -> SandboxExecutor -> Grader.
 * MUST NOT throw GameException for player faults (a rejected source is returned as a failed GradeReport).
 * Throws GameException(QUEST_PROFILE_UNSUPPORTED) if quest.profile() is not BASIC in this feature.
 */
public interface Evaluator {
    GradeReport evaluate(Quest quest, TestScope scope, String playerSource, String runId);
}
