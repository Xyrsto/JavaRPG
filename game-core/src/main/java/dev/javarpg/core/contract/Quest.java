package dev.javarpg.core.contract;

import java.util.List;

/** region is the one-letter region code (V,F,G,L,D,C,A). hints always has exactly 3 entries. */
public record Quest(
        String id,
        String region,
        String title,
        String concept,
        String story,
        String goal,
        ChallengeKind kind,
        String starterCode,
        Profile profile,
        List<TestCase> visibleTests,
        List<TestCase> hiddenTests,
        List<String> hints,
        String referenceSolution,
        String explanation,
        List<CommonMistake> commonMistakes,
        WorldEffect worldEffect) { }
