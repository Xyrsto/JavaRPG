package dev.javarpg.server.repository;

import static dev.javarpg.server.repository.ContentChecks.check;
import static dev.javarpg.server.repository.ContentChecks.nonBlank;
import static dev.javarpg.server.repository.ContentChecks.parseEnum;
import static dev.javarpg.server.repository.ContentChecks.requireLines;
import static dev.javarpg.server.repository.ContentChecks.requireText;

import dev.javarpg.core.contract.ChallengeKind;
import dev.javarpg.core.contract.CommonMistake;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.WorldEffect;
import dev.javarpg.core.rules.Curriculum;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.server.dto.QuestFileDto;
import java.util.List;

/**
 * Validates one parsed quest file and maps it to the domain Quest. The order of the checks decides which
 * problem is reported first, so it is part of the behaviour. Detail checks live in the small mappers.
 */
final class QuestFileMapper {

    private final TestCaseMapper tests;
    private final CommonMistakeMapper mistakes;
    private final WorldEffectMapper effects;

    QuestFileMapper(WorldDef world) {
        this(new TestCaseMapper(), new CommonMistakeMapper(), new WorldEffectMapper(world));
    }

    QuestFileMapper(TestCaseMapper tests, CommonMistakeMapper mistakes, WorldEffectMapper effects) {
        this.tests = tests;
        this.mistakes = mistakes;
        this.effects = effects;
    }

    Quest toQuest(String name, QuestFileDto dto) {
        String id = dto.id();
        check(name, id != null && name.equals(id + ".json"), "id", "must match the file name");
        check(name, Curriculum.isValidQuestId(id), "id", "must be a valid curriculum id such as V01");
        check(name, dto.region() != null && dto.region().equals(id.substring(0, 1)), "region",
                "must be the region letter of the id");
        ChallengeKind kind = parseEnum(name, "kind", null, dto.kind(), ChallengeKind.class);
        Profile profile = parseEnum(name, "profile", null, dto.profile(), Profile.class);
        check(name, profile == Curriculum.expectedProfile(id), "profile",
                "must be " + Curriculum.expectedProfile(id) + " for " + id);

        requireText(name, "title", dto.title());
        requireText(name, "concept", dto.concept());
        requireText(name, "story", dto.story());
        requireText(name, "goal", dto.goal());
        requireText(name, "explanation", dto.explanation());
        List<String> starter = requireLines(name, "starterCode", dto.starterCode());
        List<String> reference = requireLines(name, "referenceSolution", dto.referenceSolution());

        check(name, dto.hints() != null && dto.hints().size() == 3, "hints", "must contain exactly 3 hints");
        for (String hint : dto.hints()) {
            check(name, nonBlank(hint), "hints", "must not contain blank hints");
        }

        List<TestCase> visible = tests.map(name, "visibleTests", dto.visibleTests(), kind);
        List<TestCase> hidden = tests.map(name, "hiddenTests", dto.hiddenTests(), kind);
        List<CommonMistake> commonMistakes = mistakes.map(name, dto.commonMistakes());
        WorldEffect effect = effects.map(name, dto.worldEffect());

        return new Quest(id, dto.region(), dto.title(), dto.concept(), dto.story(), dto.goal(), kind,
                join(starter), profile, visible, hidden, List.copyOf(dto.hints()), join(reference),
                dto.explanation(), commonMistakes, effect);
    }

    private static String join(List<String> lines) {
        return String.join("\n", lines) + "\n";
    }
}
