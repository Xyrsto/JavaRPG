package dev.javarpg.server.docs;

import static dev.javarpg.server.support.TestSupport.API;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.javarpg.server.support.TestSupport;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** NFR-004, FR-012: the page loads nothing from other hosts, and the game filters still protect run and submit. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocsPageSafetyTest {

    private static final Pattern EXTERNAL_HOST = Pattern.compile("https?://(?!localhost|127\\.0\\.0\\.1)[A-Za-z0-9.-]+");

    @Autowired
    private MockMvc mvc;

    private MockHttpServletResponse fetch(String address) throws Exception {
        return mvc.perform(get(address)).andReturn().getResponse();
    }

    @Test
    void theBrowserPageIsServedAndReferencesNoOtherHost() throws Exception {
        MockHttpServletResponse entry = fetch("/docs");
        assertTrue(entry.getStatus() == 200 || entry.getStatus() / 100 == 3, "/docs answered " + entry.getStatus());
        for (String address : new String[] {"/swagger-ui/index.html", "/swagger-ui/swagger-initializer.js",
                "/v3/api-docs/swagger-config", "/v3/api-docs", "/v3/api-docs.yaml"}) {
            MockHttpServletResponse response = fetch(address);
            assertEquals(200, response.getStatus(), address);
            String body = response.getContentAsString();
            assertFalse(body.isBlank(), address);
            assertFalse(EXTERNAL_HOST.matcher(body).find(), address + " mentions an external host: "
                    + (EXTERNAL_HOST.matcher(body).find() ? EXTERNAL_HOST.matcher(body).group() : ""));
        }
    }

    @Test
    void theDescriptionContainsNoPlayerDataOrLocalPaths() throws Exception {
        String player = TestSupport.createPlayer(mvc);
        String spec = fetch("/v3/api-docs.yaml").getContentAsString();
        assertFalse(spec.contains(player), "a real player id leaked into the description");
        assertFalse(Pattern.compile("\\b[A-Za-z]:\\\\|/Users/|/home/").matcher(spec).find(), "local path in the description");
    }

    @Test
    void theBodyLimitStillAppliesToRunAndSubmit() throws Exception {
        String player = TestSupport.createPlayer(mvc);
        String huge = TestSupport.sourceBody("x".repeat(200_000));
        for (String operation : new String[] {"run", "submit"}) {
            MockHttpServletResponse response = mvc.perform(post(API + "/players/" + player + "/quests/V01/" + operation)
                    .contentType("application/json").content(huge)).andReturn().getResponse();
            assertEquals(413, response.getStatus(), operation);
            assertTrue(response.getContentAsString().contains("SOURCE_TOO_LARGE"), operation);
        }
    }
}
