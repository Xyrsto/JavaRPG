package dev.javarpg.server.repository;

import static dev.javarpg.server.repository.ContentChecks.check;
import static dev.javarpg.server.repository.ContentChecks.checkIn;
import static dev.javarpg.server.repository.ContentChecks.nonBlank;
import static dev.javarpg.server.repository.ContentChecks.parseEnum;
import static dev.javarpg.server.repository.ContentChecks.requireSafeRegex;

import dev.javarpg.core.contract.ChallengeKind;
import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.server.dto.QuestFileDto.TestFileDto;
import java.util.ArrayList;
import java.util.List;

/** Validates the visible or hidden tests of one quest file and maps them to domain TestCases. */
final class TestCaseMapper {

    /** What a test of a given ChallengeKind may say about its call and match mode. */
    private interface KindRule {
        void check(String file, String where, String call, MatchMode mode);
    }

    private static final KindRule STDOUT_RULE = (file, where, call, mode) ->
            checkIn(file, call == null, "call", where, "must be omitted for STDOUT quests");

    private static final KindRule METHOD_RETURN_RULE = (file, where, call, mode) -> {
        checkIn(file, MethodCallWhitelist.isAllowed(call), "call", where,
                "must look like Solution.method(args) with only literal arguments, "
                        + "no ';', no comments and no nested calls");
        checkIn(file, mode == MatchMode.EXACT_TRIMMED, "match", where,
                "must be EXACT_TRIMMED for METHOD_RETURN quests");
    };

    List<TestCase> map(String file, String field, List<TestFileDto> dtos, ChallengeKind kind) {
        check(file, dtos != null && !dtos.isEmpty(), field, "needs at least one test");
        KindRule rule = ruleFor(kind);
        List<TestCase> out = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            TestFileDto t = dtos.get(i);
            String where = field + "[" + i + "]";
            checkIn(file, nonBlank(t.name()), "name", where, "must not be blank");
            MatchMode mode = parseEnum(file, "match", where, t.match(), MatchMode.class);
            checkIn(file, t.expected() != null, "expected", where, "must be given");
            rule.check(file, where, t.call(), mode);
            if (mode == MatchMode.SOURCE_REGEX) {
                requireSafeRegex(file, "expected", where, t.expected());
            }
            out.add(new TestCase(t.name(), t.call(), t.expected(), mode));
        }
        return out;
    }

    private static KindRule ruleFor(ChallengeKind kind) {
        return switch (kind) {
            case STDOUT -> STDOUT_RULE;
            case METHOD_RETURN -> METHOD_RETURN_RULE;
        };
    }
}
