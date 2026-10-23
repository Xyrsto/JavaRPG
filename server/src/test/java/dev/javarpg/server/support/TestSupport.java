package dev.javarpg.server.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.core.contract.SandboxRequest;
import dev.javarpg.core.contract.SandboxResult;
import dev.javarpg.core.contract.SandboxStatus;
import dev.javarpg.runner.fake.FakeSandboxExecutor;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Shared helpers for the server tests. */
public final class TestSupport {

    public static final String API = "/api/v1";
    public static final String NIL_PLAYER = "00000000-0000-4000-8000-000000000000";
    public static final String V01_OUTPUT = "Hello, Adventurer!\n";
    public static final String V02_OUTPUT = "Fire: 7\nWater: 2.5\nEarth: true\nAir: A\nTotal: 12\n";

    public static final String V01_SOLUTION = lines(
            "public class Main {",
            "    public static void main(String[] args) {",
            "        String name = \"Adventurer\";",
            "        System.out.println(\"Hello, \" + name + \"!\");",
            "    }",
            "}");

    public static final String V02_SOLUTION = lines(
            "public class Main {",
            "    public static void main(String[] args) {",
            "        int fire = 7;",
            "        double water = 2.5;",
            "        boolean earth = true;",
            "        char air = 'A';",
            "        System.out.println(\"Fire: \" + fire);",
            "        System.out.println(\"Water: \" + water);",
            "        System.out.println(\"Earth: \" + earth);",
            "        System.out.println(\"Air: \" + air);",
            "        int bonus = 5;",
            "        System.out.println(\"Total: \" + (fire + bonus));",
            "    }",
            "}");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestSupport() {
    }

    public static String lines(String... rows) {
        return String.join("\n", rows) + "\n";
    }

    /** JSON string literal for the given text. */
    public static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    public static String sourceBody(String source) {
        return "{\"source\":" + quote(source) + "}";
    }

    public static String moveBody(String... directions) {
        StringBuilder out = new StringBuilder("{\"directions\":[");
        for (int i = 0; i < directions.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(directions[i]));
        }
        return out.append("]}").toString();
    }

    public static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Response is not JSON: " + body, e);
        }
    }

    public static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    public static ResultActions getJson(MockMvc mvc, String path) throws Exception {
        return mvc.perform(get(API + path));
    }

    public static ResultActions postJson(MockMvc mvc, String path, String json) throws Exception {
        return mvc.perform(post(API + path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public static ResultActions postEmpty(MockMvc mvc, String path) throws Exception {
        return mvc.perform(post(API + path));
    }

    public static String createPlayer(MockMvc mvc) throws Exception {
        ResultActions result = postEmpty(mvc, "/players");
        result.andExpect(status().isCreated());
        return parse(body(result)).get("playerId").asText();
    }

    public static ResultActions move(MockMvc mvc, String playerId, String... directions) throws Exception {
        return postJson(mvc, "/players/" + playerId + "/move", moveBody(directions));
    }

    public static ResultActions interact(MockMvc mvc, String playerId) throws Exception {
        return postEmpty(mvc, "/players/" + playerId + "/interact");
    }

    public static ResultActions run(MockMvc mvc, String playerId, String questId, String source) throws Exception {
        return postJson(mvc, "/players/" + playerId + "/quests/" + questId + "/run", sourceBody(source));
    }

    public static ResultActions submit(MockMvc mvc, String playerId, String questId, String source) throws Exception {
        return postJson(mvc, "/players/" + playerId + "/quests/" + questId + "/submit", sourceBody(source));
    }

    public static ResultActions hint(MockMvc mvc, String playerId, String questId) throws Exception {
        return postEmpty(mvc, "/players/" + playerId + "/quests/" + questId + "/hints/next");
    }

    /** Makes the fake sandbox answer every run with the given handler. */
    public static void program(SandboxExecutor executor, Function<SandboxRequest, SandboxResult> handler) {
        ((FakeSandboxExecutor) executor).setHandler(handler);
    }

    public static void reset(SandboxExecutor executor) {
        program(executor, request -> ok(""));
    }

    /** Completes V01 for the player through the real API, using the fake sandbox. */
    public static void completeV01(MockMvc mvc, SandboxExecutor executor, String playerId) throws Exception {
        program(executor, request -> ok(V01_OUTPUT));
        submit(mvc, playerId, "V01", V01_SOLUTION).andExpect(status().isOk());
    }

    public static SandboxResult ok(String stdout) {
        return new SandboxResult(SandboxStatus.OK, 0, "", stdout, "", false, 5L);
    }

    public static SandboxResult compileError(String compilerOutput) {
        return new SandboxResult(SandboxStatus.COMPILE_ERROR, 1, compilerOutput, "", "", false, 5L);
    }

    public static SandboxResult timeout() {
        return new SandboxResult(SandboxStatus.TIMEOUT, -1, "", "", "", false, 3000L);
    }

    public static SandboxResult runtimeError(String stderr) {
        return new SandboxResult(SandboxStatus.RUNTIME_ERROR, 1, "", "", stderr, false, 5L);
    }

    public static SandboxResult sandboxError(String message) {
        return new SandboxResult(SandboxStatus.SANDBOX_ERROR, -1, "", "", message, false, 0L);
    }
}
