package dev.javarpg.server.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** FR-004: fingerprinted assets are cached for a long time, everything else is not, and nothing else is exposed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaticWebTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void fingerprintedAssetsAreServedWithALongImmutableCache() throws Exception {
        mvc.perform(get("/assets/fixture.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.allOf(
                        Matchers.containsString("max-age=31536000"), Matchers.containsString("immutable"))));
    }

    @Test
    void otherStaticFilesAreNotCachedForLong() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/fixture.txt")).andExpect(status().isOk()).andReturn().getResponse();
        String cache = String.valueOf(response.getHeader("Cache-Control"));
        assertFalse(cache.contains("immutable") || cache.contains("max-age=31536000"), cache);
    }

    @Test
    void anUnknownFileAnswersWithTheStandardNotFoundBody() throws Exception {
        mvc.perform(get("/assets/missing.js"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/nope.js"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void pathTricksNeverExposeFilesOutsideTheStaticFolder() throws Exception {
        for (String path : new String[] {"/assets/..%2Fapplication.properties", "/assets/../application.properties",
                "/%2e%2e/application.properties", "/assets/%2e%2e/%2e%2e/application.properties", "/static/fixture.txt",
                "/openapi/operations.yaml", "/META-INF/spring.factories"}) {
            MockHttpServletResponse response = mvc.perform(get(path)).andReturn().getResponse();
            String body = response.getContentAsString();
            assertTrue(response.getStatus() != 200 || !body.contains("javarpg."), path + " leaked a file");
            assertFalse(body.contains("javarpg.sandbox"), path);
            assertFalse(body.contains("DocsSwitch"), path);
        }
    }
}
