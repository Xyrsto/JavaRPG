package dev.javarpg.server.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.javarpg.server.config.JavaRpgProperties;
import dev.javarpg.server.support.MutableClock;
import dev.javarpg.server.util.TokenBucketRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Characterization test (spec 005): the filters write their error bodies by hand, so the exact bytes, status,
 * content type and headers are pinned here. Written against the code before error codes were unified.
 */
class FilterErrorBodiesUnchangedTest {

    @RestController
    static class Stub {
        @GetMapping("/ping")
        String ping() {
            return "pong";
        }

        @PostMapping("/api/v1/players")
        String create(@RequestBody(required = false) String body) {
            return "ok";
        }
    }

    private static void expectExact(MockHttpServletResponse response, int status, String body) throws Exception {
        assertEquals(status, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertEquals(body, response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void hostHeaderRefusalKeepsItsExactBody() throws Exception {
        JavaRpgProperties props = new JavaRpgProperties();
        props.getSandbox().setMode("unsafe-local");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Stub()).addFilters(new HostHeaderFilter(props)).build();
        MockHttpServletResponse response = mvc.perform(get("/ping").header("Host", "evil.example.com"))
                .andReturn().getResponse();
        expectExact(response, 403,
                "{\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"This server only answers on localhost.\"}}");
    }

    @Test
    void rateLimitRefusalKeepsItsExactBodyAndRetryAfter() throws Exception {
        MutableClock clock = new MutableClock();
        JavaRpgProperties props = new JavaRpgProperties();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Stub())
                .addFilters(new RateLimitFilter(new TokenBucketRateLimiter(clock, 100), props)).build();
        MockHttpServletResponse last = null;
        for (int i = 0; i < 6; i++) {
            last = mvc.perform(post("/api/v1/players")).andReturn().getResponse();
        }
        expectExact(last, 429, "{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":"
                + "\"You are going a little fast. Wait a few seconds and try again.\"}}");
        String retryAfter = last.getHeader("Retry-After");
        assertTrue(retryAfter != null && retryAfter.matches("[1-9][0-9]*"), "Retry-After was " + retryAfter);
    }

    @Test
    void bodyTooLargeRefusalKeepsItsExactBody() throws Exception {
        JavaRpgProperties props = new JavaRpgProperties();
        props.getHttp().setMaxBodyBytes(10);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Stub()).addFilters(new BodySizeLimitFilter(props)).build();
        MockHttpServletResponse response = mvc.perform(post("/api/v1/players").contentType("application/json")
                .content("x".repeat(100))).andReturn().getResponse();
        expectExact(response, 413,
                "{\"error\":{\"code\":\"SOURCE_TOO_LARGE\",\"message\":\"That request is too large. Send less data.\"}}");
    }
}
