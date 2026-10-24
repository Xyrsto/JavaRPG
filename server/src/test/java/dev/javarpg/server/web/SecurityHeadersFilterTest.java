package dev.javarpg.server.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-004, NFR-003: the page and its files carry protective headers; the API, health, documentation and the
 * API description do not change at all.
 */
@SpringBootTest(properties = "javarpg.web.index=classpath:/test-web/index.html")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersFilterTest {

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "font-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    @Autowired
    private MockMvc mvc;

    private MockHttpServletResponse fetch(String path) throws Exception {
        return mvc.perform(get(path)).andReturn().getResponse();
    }

    @Test
    void thePageItsAssetsAndNotFoundAnswersCarryTheHeaders() throws Exception {
        for (String path : new String[] {"/", "/assets/fixture.js", "/fixture.txt", "/nope.js"}) {
            MockHttpServletResponse response = fetch(path);
            assertEquals(CSP, response.getHeader("Content-Security-Policy"), path);
            assertEquals("nosniff", response.getHeader("X-Content-Type-Options"), path);
            assertEquals("no-referrer", response.getHeader("Referrer-Policy"), path);
        }
    }

    @Test
    void theApiHealthDocumentationAndDescriptionAreLeftAlone() throws Exception {
        for (String path : new String[] {"/api/v1/world", "/api/v1/players/not-a-uuid", "/health", "/docs",
                "/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui/index.html"}) {
            MockHttpServletResponse response = fetch(path);
            assertNull(response.getHeader("Content-Security-Policy"), path);
            assertNull(response.getHeader("X-Content-Type-Options"), path);
            assertNull(response.getHeader("Referrer-Policy"), path);
        }
    }
}
