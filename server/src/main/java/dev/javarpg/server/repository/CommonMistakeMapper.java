package dev.javarpg.server.repository;

import static dev.javarpg.server.repository.ContentChecks.checkIn;
import static dev.javarpg.server.repository.ContentChecks.nonBlank;
import static dev.javarpg.server.repository.ContentChecks.parseEnum;
import static dev.javarpg.server.repository.ContentChecks.requireSafeRegex;

import dev.javarpg.core.contract.CommonMistake;
import dev.javarpg.core.contract.MistakeSource;
import dev.javarpg.server.dto.QuestFileDto.MistakeFileDto;
import java.util.ArrayList;
import java.util.List;

/** Validates the commonMistakes of one quest file (patterns are player-facing regexes, see F-15). */
final class CommonMistakeMapper {

    List<CommonMistake> map(String file, List<MistakeFileDto> dtos) {
        List<CommonMistake> out = new ArrayList<>();
        if (dtos == null) {
            return out;
        }
        for (int i = 0; i < dtos.size(); i++) {
            MistakeFileDto m = dtos.get(i);
            String where = "commonMistakes[" + i + "]";
            MistakeSource source = parseEnum(file, "source", where, m.source(), MistakeSource.class);
            requireSafeRegex(file, "pattern", where, m.pattern());
            checkIn(file, nonBlank(m.message()), "message", where, "must not be blank");
            out.add(new CommonMistake(source, m.pattern(), m.message()));
        }
        return out;
    }
}
