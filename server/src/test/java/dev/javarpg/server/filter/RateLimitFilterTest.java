package dev.javarpg.server.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.javarpg.server.config.JavaRpgProperties;
import dev.javarpg.server.support.MutableClock;
import dev.javarpg.server.util.TokenBucketRateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** F-14: per-IP token buckets answer 429 with Retry-After and recover as the clock moves. */
class RateLimitFilterTest {

    @RestController
    static class Stub {
        @PostMapping("/api/v1/players")
        String create() {
            return "ok";
        }

        @PostMapping("/api/v1/players/{p}/quests/{q}/run")
        String run() {
            return "ok";
        }

        @PostMapping("/api/v1/players/{p}/quests/{q}/submit")
        String submit() {
            return "ok";
        }

        @GetMapping("/api/v1/players/{p}")
        String read() {
            return "ok";
        }
    }

    private final MutableClock clock = new MutableClock();
    private final JavaRpgProperties props = new JavaRpgProperties();
    private final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(clock, 100);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Stub())
            .addFilters(new RateLimitFilter(limiter, props)).build();

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void defaultsAreFivePlayersAndTenRunsAndSubmitsPerMinute() {
        assertEquals(5, props.getRate().getPlayersPerMinute());
        assertEquals(10, props.getRate().getRunPerMinute());
        assertEquals(10, props.getRate().getSubmitPerMinute());
    }

    @Test
    void sixthPlayerCreationIsRefusedThenRecoversAfterTheClockAdvances() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/players").with(from("10.0.0.1"))).andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/players").with(from("10.0.0.1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.error.message").isNotEmpty());
        clock.advance(Duration.ofSeconds(13)); // 5 per minute refills one token every 12 s
        mvc.perform(post("/api/v1/players").with(from("10.0.0.1"))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/players").with(from("10.0.0.1"))).andExpect(status().isTooManyRequests());
    }

    @Test
    void otherAddressesAreNotAffected() throws Exception {
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/v1/players").with(from("10.0.0.1")));
        }
        mvc.perform(post("/api/v1/players").with(from("10.0.0.2"))).andExpect(status().isOk());
    }

    @Test
    void runAndSubmitHaveTheirOwnBuckets() throws Exception {
        String run = "/api/v1/players/p/quests/V01/run";
        String submit = "/api/v1/players/p/quests/V01/submit";
        for (int i = 0; i < 10; i++) {
            mvc.perform(post(run).with(from("10.0.0.1"))).andExpect(status().isOk());
        }
        mvc.perform(post(run).with(from("10.0.0.1"))).andExpect(status().isTooManyRequests());
        mvc.perform(post(submit).with(from("10.0.0.1"))).andExpect(status().isOk());
    }

    @Test
    void readsAreNeverLimited() throws Exception {
        for (int i = 0; i < 50; i++) {
            mvc.perform(get("/api/v1/players/p").with(from("10.0.0.1"))).andExpect(status().isOk());
        }
    }

    @Test
    void forwardedHeaderIsIgnoredUnlessTrusted() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/players").with(from("10.0.0.1")).header("X-Forwarded-For", "1.1.1." + i))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/players").with(from("10.0.0.1")).header("X-Forwarded-For", "9.9.9.9"))
                .andExpect(status().isTooManyRequests());

        props.getRate().setTrustForwarded(true);
        mvc.perform(post("/api/v1/players").with(from("10.0.0.1")).header("X-Forwarded-For", "9.9.9.9"))
                .andExpect(status().isOk());
    }

    @Test
    void trackedClientsAreBounded() {
        TokenBucketRateLimiter small = new TokenBucketRateLimiter(clock, 50);
        for (int i = 0; i < 1000; i++) {
            small.tryAcquire("k" + i, 5);
        }
        assertTrue(small.size() <= 50, "size " + small.size());
    }

    @Test
    void zeroDisablesALimit() {
        for (int i = 0; i < 100; i++) {
            assertEquals(0L, limiter.tryAcquire("x", 0));
        }
    }
}
