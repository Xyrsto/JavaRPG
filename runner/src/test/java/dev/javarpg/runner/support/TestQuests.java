package dev.javarpg.runner.support;

import dev.javarpg.core.contract.ChallengeKind;
import dev.javarpg.core.contract.CommonMistake;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.TestCase;
import java.util.List;

public final class TestQuests {

    private TestQuests() {
    }

    public static Quest stdoutQuest(List<TestCase> visible, List<TestCase> hidden) {
        return quest(ChallengeKind.STDOUT, Profile.BASIC, visible, hidden, List.of());
    }

    public static Quest methodQuest(List<TestCase> visible, List<TestCase> hidden) {
        return quest(ChallengeKind.METHOD_RETURN, Profile.BASIC, visible, hidden, List.of());
    }

    public static Quest quest(ChallengeKind kind, Profile profile, List<TestCase> visible,
                              List<TestCase> hidden, List<CommonMistake> mistakes) {
        return new Quest("V99", "V", "Test quest", "Test concept", "Story", "Goal", kind,
                "public class Main { }", profile, List.copyOf(visible), List.copyOf(hidden),
                List.of("Hint one", "Hint two", "Hint three"), "public class Main { }", "Explanation",
                List.copyOf(mistakes), null);
    }

    public static Quest withProfile(Quest q, Profile profile) {
        return new Quest(q.id(), q.region(), q.title(), q.concept(), q.story(), q.goal(), q.kind(),
                q.starterCode(), profile, q.visibleTests(), q.hiddenTests(), q.hints(),
                q.referenceSolution(), q.explanation(), q.commonMistakes(), q.worldEffect());
    }
}
