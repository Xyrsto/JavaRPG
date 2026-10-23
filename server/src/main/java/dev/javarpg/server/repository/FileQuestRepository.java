package dev.javarpg.server.repository;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.javarpg.core.contract.ChallengeKind;
import dev.javarpg.core.contract.CommonMistake;
import dev.javarpg.core.contract.MatchMode;
import dev.javarpg.core.contract.MistakeSource;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.contract.QuestRepository;
import dev.javarpg.core.contract.TestCase;
import dev.javarpg.core.contract.WorldEffect;
import dev.javarpg.core.rules.Curriculum;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.server.dto.QuestFileDto;
import dev.javarpg.server.exception.ContentLoadException;
import dev.javarpg.server.util.RegexLint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/** Loads and validates every quest file in a directory at startup (plan 5.9). Fails fast on any problem. */
public final class FileQuestRepository implements QuestRepository {

    /**
     * Strict whitelist for a METHOD_RETURN call (security review F-15): Solution.method( args ) where each
     * arg is a number, string or char literal, true/false/null, or new T[]{literals}. No nested calls, no
     * ';', no comments, no ")(" sequence. The call text ends up in generated Java source, so it must stay
     * data, never code.
     */
    private static final String SCALAR = "(?:-?[0-9]+(?:\\.[0-9]+)?[LlFfDd]?"
            + "|\"(?:[^\"\\\\;/()]|\\\\[\"\\\\nt])*\""
            + "|'(?:[^'\\\\;/()]|\\\\['\\\\nt])'"
            + "|true|false|null)";
    private static final String ARRAY = "new[ ]+[A-Za-z_][A-Za-z0-9_]*[ ]*\\[[ ]*\\][ ]*\\{[ ]*(?:" + SCALAR
            + "(?:[ ]*,[ ]*" + SCALAR + ")*)?[ ]*\\}";
    private static final String ARG = "(?:" + SCALAR + "|" + ARRAY + ")";
    private static final Pattern METHOD_CALL = Pattern.compile("^Solution\\.[A-Za-z_][A-Za-z0-9_]*\\([ ]*(?:" + ARG
            + "(?:[ ]*,[ ]*" + ARG + ")*)?[ ]*\\)$");
    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private final List<Quest> ordered;
    private final Map<String, Quest> byId;

    public FileQuestRepository(Path directory, WorldDef world) {
        List<Quest> loaded = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Path file : jsonFiles(directory)) {
            Quest quest = load(file, world);
            if (!seen.add(quest.id())) {
                throw new ContentLoadException(file.getFileName() + ": duplicate quest id " + quest.id());
            }
            loaded.add(quest);
        }
        loaded.sort(Comparator.comparingInt((Quest q) -> Curriculum.globalIndex(q.id())));
        this.ordered = List.copyOf(loaded);
        Map<String, Quest> index = new HashMap<>();
        for (Quest quest : ordered) {
            index.put(quest.id(), quest);
        }
        this.byId = Map.copyOf(index);
    }

    @Override
    public Optional<Quest> findById(String questId) {
        return Optional.ofNullable(byId.get(questId));
    }

    @Override
    public List<Quest> findAllOrdered() {
        return ordered;
    }

    private static List<Path> jsonFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new ContentLoadException("Quest folder not found: " + directory.toAbsolutePath());
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new ContentLoadException("Cannot list the quest folder " + directory.toAbsolutePath(), e);
        }
    }

    private static Quest load(Path file, WorldDef world) {
        String name = file.getFileName().toString();
        QuestFileDto dto;
        try {
            dto = MAPPER.readValue(Files.readString(file), QuestFileDto.class);
        } catch (IOException | RuntimeException e) {
            throw new ContentLoadException(name + ": invalid JSON or unknown field: " + e.getMessage(), e);
        }

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

        List<TestCase> visible = tests(name, "visibleTests", dto.visibleTests(), kind);
        List<TestCase> hidden = tests(name, "hiddenTests", dto.hiddenTests(), kind);
        List<CommonMistake> mistakes = mistakes(name, dto.commonMistakes());
        WorldEffect effect = worldEffect(name, dto.worldEffect(), world);

        return new Quest(id, dto.region(), dto.title(), dto.concept(), dto.story(), dto.goal(), kind,
                join(starter), profile, visible, hidden, List.copyOf(dto.hints()), join(reference),
                dto.explanation(), mistakes, effect);
    }

    private static List<TestCase> tests(String file, String field, List<QuestFileDto.TestFileDto> dtos,
                                        ChallengeKind kind) {
        check(file, dtos != null && !dtos.isEmpty(), field, "needs at least one test");
        List<TestCase> out = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            QuestFileDto.TestFileDto t = dtos.get(i);
            String where = field + "[" + i + "]";
            checkIn(file, nonBlank(t.name()), "name", where, "must not be blank");
            MatchMode mode = parseEnum(file, "match", where, t.match(), MatchMode.class);
            checkIn(file, t.expected() != null, "expected", where, "must be given");
            String call = t.call();
            if (kind == ChallengeKind.STDOUT) {
                checkIn(file, call == null, "call", where, "must be omitted for STDOUT quests");
            } else {
                checkIn(file, call != null && METHOD_CALL.matcher(call).matches()
                                && !call.contains(";") && !call.contains("//") && !call.contains("/*")
                                && !call.matches("(?s).*\\)\\s*\\(.*"),
                        "call", where, "must look like Solution.method(args) with only literal arguments, "
                                + "no ';', no comments and no nested calls");
                checkIn(file, mode == MatchMode.EXACT_TRIMMED, "match", where,
                        "must be EXACT_TRIMMED for METHOD_RETURN quests");
            }
            if (mode == MatchMode.SOURCE_REGEX) {
                compileRegex(file, "expected", where, t.expected());
            }
            out.add(new TestCase(t.name(), call, t.expected(), mode));
        }
        return out;
    }

    private static List<CommonMistake> mistakes(String file, List<QuestFileDto.MistakeFileDto> dtos) {
        List<CommonMistake> out = new ArrayList<>();
        if (dtos == null) {
            return out;
        }
        for (int i = 0; i < dtos.size(); i++) {
            QuestFileDto.MistakeFileDto m = dtos.get(i);
            String where = "commonMistakes[" + i + "]";
            MistakeSource source = parseEnum(file, "source", where, m.source(), MistakeSource.class);
            compileRegex(file, "pattern", where, m.pattern());
            checkIn(file, nonBlank(m.message()), "message", where, "must not be blank");
            out.add(new CommonMistake(source, m.pattern(), m.message()));
        }
        return out;
    }

    private static WorldEffect worldEffect(String file, QuestFileDto.WorldEffectFileDto dto, WorldDef world) {
        if (dto == null) {
            return null;
        }
        check(file, "OPEN_GATE".equals(dto.type()), "worldEffect.type", "must be OPEN_GATE");
        check(file, isGateOnMap(world, dto.target()), "worldEffect.target", "must name a gate on the map");
        check(file, nonBlank(dto.message()), "worldEffect.message", "must not be blank");
        return new WorldEffect(dto.type(), dto.target(), dto.message());
    }

    private static boolean isGateOnMap(WorldDef world, String target) {
        if (target == null || target.length() != 1) {
            return false;
        }
        char letter = target.charAt(0);
        if (letter < 'A' || letter > 'Z') {
            return false;
        }
        for (String row : world.map().rows()) {
            if (row.indexOf(letter) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static void compileRegex(String file, String key, String where, String regex) {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException | NullPointerException e) {
            checkIn(file, false, key, where, "is not a valid Java regular expression");
        }
        checkIn(file, !RegexLint.hasNestedQuantifier(regex), key, where,
                "has nested quantifiers such as (a+)+ that can backtrack catastrophically; rewrite the pattern");
    }

    private static <E extends Enum<E>> E parseEnum(String file, String key, String where, String value,
                                                   Class<E> type) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException e) {
            checkIn(file, false, key, where, "must be one of " + Arrays.toString(type.getEnumConstants()));
            throw new IllegalStateException("unreachable");
        }
    }

    private static List<String> requireLines(String file, String key, List<String> lines) {
        check(file, lines != null && !lines.isEmpty(), key, "must not be empty");
        for (String line : lines) {
            check(file, line != null, key, "must not contain null lines");
        }
        return lines;
    }

    private static void requireText(String file, String key, String value) {
        check(file, nonBlank(value), key, "must not be blank");
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String join(List<String> lines) {
        return String.join("\n", lines) + "\n";
    }

    private static void check(String file, boolean ok, String key, String detail) {
        if (!ok) {
            throw new ContentLoadException(file + ": '" + key + "' " + detail);
        }
    }

    private static void checkIn(String file, boolean ok, String key, String where, String detail) {
        if (!ok) {
            throw new ContentLoadException(file + ": '" + key + "' in " + where + " " + detail);
        }
    }
}
