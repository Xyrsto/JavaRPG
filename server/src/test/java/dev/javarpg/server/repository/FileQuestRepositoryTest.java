package dev.javarpg.server.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.javarpg.core.contract.Profile;
import dev.javarpg.core.contract.Quest;
import dev.javarpg.core.rules.Curriculum;
import dev.javarpg.core.world.WorldDef;
import dev.javarpg.server.exception.ContentLoadException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileQuestRepositoryTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final WorldDef WORLD = new WorldLoader().load(Path.of("../content/world/village.json"));

    @TempDir
    Path dir;

    @Test
    void loadsAValidQuestFile() throws Exception {
        write("V01.json", validMap("V01"));
        Quest quest = new FileQuestRepository(dir, WORLD).findById("V01").orElseThrow();
        assertEquals("Title V01", quest.title());
        assertEquals(Profile.BASIC, quest.profile());
        assertEquals(3, quest.hints().size());
    }

    @Test
    void ordersQuestsInCurriculumOrder() throws Exception {
        write("V02.json", validMap("V02"));
        write("V01.json", validMap("V01"));
        assertEquals(List.of("V01", "V02"), ids(new FileQuestRepository(dir, WORLD).findAllOrdered()));
    }

    @Test
    void loadsAll74QuestsInCurriculumOrder() throws Exception {
        List<String> expected = new ArrayList<>();
        for (Curriculum.Region region : Curriculum.regions()) {
            for (int i = 1; i <= region.questCount(); i++) {
                String id = region.code() + String.format("%02d", i);
                expected.add(id);
                write(id + ".json", validMap(id));
            }
        }
        assertEquals(74, expected.size());
        assertEquals(expected, ids(new FileQuestRepository(dir, WORLD).findAllOrdered()));
    }

    @Test
    void joinsLineArraysWithNewlinesAndATrailingNewline() throws Exception {
        write("V01.json", validMap("V01"));
        Quest quest = new FileQuestRepository(dir, WORLD).findById("V01").orElseThrow();
        assertEquals("public class Main {\n    public static void main(String[] args) {\n        // TODO\n    }\n}\n",
                quest.starterCode());
    }

    @Test
    void rejectsFileNameThatDoesNotMatchTheId() throws Exception {
        assertRejected("V09.json", validMap("V01"), "id");
    }

    @Test
    void rejectsAnInvalidId() throws Exception {
        assertRejected("V11.json", validMap("V11"), "id");
    }

    @Test
    void rejectsRegionThatDoesNotMatchTheId() throws Exception {
        Map<String, Object> m = validMap("V01");
        m.put("region", "F");
        assertRejected("V01.json", m, "region");
    }

    @Test
    void rejectsProfileThatDoesNotMatchTheCurriculum() throws Exception {
        Map<String, Object> m = validMap("V01");
        m.put("profile", "TESTING");
        assertRejected("V01.json", m, "profile");
    }

    @Test
    void rejectsAHintCountOtherThanThree() throws Exception {
        Map<String, Object> m = validMap("V01");
        m.put("hints", List.of("only one", "two"));
        assertRejected("V01.json", m, "hints");
    }

    @Test
    void rejectsAQuestWithoutHiddenTests() throws Exception {
        Map<String, Object> m = validMap("V01");
        m.put("hiddenTests", List.of());
        assertRejected("V01.json", m, "hiddenTests");
    }

    @Test
    void rejectsABadRegex() throws Exception {
        Map<String, Object> m = validMap("V01");
        m.put("visibleTests", List.of(test("broken", "([", "SOURCE_REGEX")));
        assertRejected("V01.json", m, "expected");
    }

    @Test
    void rejectsAMethodCallContainingASemicolon() throws Exception {
        Map<String, Object> m = validMap("V08");
        m.put("kind", "METHOD_RETURN");
        m.put("visibleTests", List.of(testWithCall("adds", "Solution.add(2, 3);", "5", "EXACT_TRIMMED")));
        assertRejected("V08.json", m, "call");
    }

    @Test
    void rejectsAnUnknownJsonProperty() throws Exception {
        Map<String, Object> m = validMap("V01");
        m.put("bogus", "x");
        write("V01.json", m);
        ContentLoadException e = assertThrows(ContentLoadException.class, () -> new FileQuestRepository(dir, WORLD));
        assertTrue(e.getMessage().contains("V01.json"), e.getMessage());
        assertTrue(e.getMessage().contains("bogus"), e.getMessage());
    }

    @Test
    void rejectsAWorldEffectTargetThatIsNotAGate() throws Exception {
        Map<String, Object> m = validMap("V01");
        Map<String, Object> effect = new LinkedHashMap<>();
        effect.put("type", "OPEN_GATE");
        effect.put("target", "Z");
        effect.put("message", "Nothing happens.");
        m.put("worldEffect", effect);
        assertRejected("V01.json", m, "worldEffect.target");
    }

    @Test
    void rejectsANestedQuantifierRegexInASourceRegexTest() throws Exception {
        for (String evil : new String[] {"(a+)+", "(a*)*", "(.*)*", "^(\\s*\\w+)*$"}) {
            Map<String, Object> m = validMap("V01");
            m.put("visibleTests", List.of(test("evil", evil, "SOURCE_REGEX")));
            ContentLoadException e = assertThrows(ContentLoadException.class, () -> {
                write("V01.json", m);
                new FileQuestRepository(dir, WORLD);
            }, evil);
            assertTrue(e.getMessage().contains("nested quantifiers"), e.getMessage());
        }
    }

    @Test
    void rejectsANestedQuantifierRegexInACommonMistake() throws Exception {
        Map<String, Object> m = validMap("V01");
        Map<String, Object> mistake = new LinkedHashMap<>();
        mistake.put("source", "STDOUT");
        mistake.put("pattern", "(x+)+y");
        mistake.put("message", "Careful.");
        m.put("commonMistakes", List.of(mistake));
        assertRejected("V01.json", m, "pattern");
    }

    @Test
    void acceptsOrdinaryRegexes() throws Exception {
        Map<String, Object> m = validMap("V01");
        m.put("visibleTests", List.of(test("ok", "System\\s*\\.\\s*out\\s*\\.\\s*println\\s*\\(.*\\bname\\b", "SOURCE_REGEX"),
                test("alt", "(int|double)\\s+x+", "SOURCE_REGEX")));
        write("V01.json", m);
        new FileQuestRepository(dir, WORLD);
    }

    @Test
    void methodCallWhitelistRejectsInjectionShapes() throws Exception {
        for (String call : new String[] {"Solution.a(1)) + evil((2)", "Solution.a(System.exit(0))",
                "Solution.a(1) // x", "Solution.a(/*c*/1)", "Solution.a(b)", "Solution.a(1)(2)"}) {
            Map<String, Object> m = validMap("V08");
            m.put("kind", "METHOD_RETURN");
            m.put("visibleTests", List.of(testWithCall("x", call, "5", "EXACT_TRIMMED")));
            assertRejected("V08.json", m, "call");
        }
    }

    @Test
    void methodCallWhitelistAcceptsLiteralArguments() throws Exception {
        Map<String, Object> m = validMap("V08");
        m.put("kind", "METHOD_RETURN");
        m.put("visibleTests", List.of(testWithCall("a", "Solution.add(2, -3)", "5", "EXACT_TRIMMED"),
                testWithCall("b", "Solution.name(\"Mira\", 'c', true, 2.5)", "x", "EXACT_TRIMMED"),
                testWithCall("c", "Solution.sum(new int[]{1, 2, 3})", "6", "EXACT_TRIMMED")));
        write("V08.json", m);
        new FileQuestRepository(dir, WORLD);
    }

    private void assertRejected(String fileName, Map<String, Object> m, String field) throws Exception {
        write(fileName, m);
        ContentLoadException e = assertThrows(ContentLoadException.class, () -> new FileQuestRepository(dir, WORLD));
        assertTrue(e.getMessage().contains(fileName), e.getMessage());
        assertTrue(e.getMessage().contains("'" + field + "'"), e.getMessage());
    }

    private void write(String fileName, Map<String, Object> m) throws Exception {
        Files.writeString(dir.resolve(fileName), JSON.writeValueAsString(m));
    }

    private static List<String> ids(List<Quest> quests) {
        List<String> result = new ArrayList<>();
        for (Quest quest : quests) {
            result.add(quest.id());
        }
        return result;
    }

    private static String profileFor(String id) {
        return Curriculum.isValidQuestId(id) ? Curriculum.expectedProfile(id).name() : "BASIC";
    }

    private static Map<String, Object> validMap(String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("region", id.substring(0, 1));
        m.put("title", "Title " + id);
        m.put("concept", "Concept");
        m.put("story", "Story");
        m.put("goal", "Goal");
        m.put("kind", "STDOUT");
        m.put("starterCode", List.of("public class Main {", "    public static void main(String[] args) {",
                "        // TODO", "    }", "}"));
        m.put("profile", profileFor(id));
        m.put("visibleTests", List.of(test("prints", "Hi", "EXACT_TRIMMED")));
        m.put("hiddenTests", List.of(test("hidden", "Hi", "EXACT_TRIMMED")));
        m.put("hints", List.of("Hint one", "Hint two", "Hint three"));
        m.put("referenceSolution", List.of("public class Main {", "    public static void main(String[] args) {",
                "        System.out.println(\"Hi\");", "    }", "}"));
        m.put("explanation", "Explanation");
        m.put("commonMistakes", List.of());
        m.put("worldEffect", null);
        return m;
    }

    private static Map<String, Object> test(String name, String expected, String match) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("expected", expected);
        t.put("match", match);
        return t;
    }

    private static Map<String, Object> testWithCall(String name, String call, String expected, String match) {
        Map<String, Object> t = test(name, expected, match);
        t.put("call", call);
        return t;
    }
}
