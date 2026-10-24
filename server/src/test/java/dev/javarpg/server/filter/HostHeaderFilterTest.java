package dev.javarpg.server.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.javarpg.server.config.JavaRpgProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** F-12a: in unsafe-local mode only localhost Host headers are served. */
class HostHeaderFilterTest {

    @RestController
    static class Ping {
        @GetMapping("/ping")
        String ping() {
            return "pong";
        }
    }

    private static MockMvc mvc(String mode) {
        JavaRpgProperties props = new JavaRpgProperties();
        props.getSandbox().setMode(mode);
        return MockMvcBuilders.standaloneSetup(new Ping()).addFilters(new HostHeaderFilter(props)).build();
    }

    @Test
    void foreignHostIsRejectedInUnsafeLocalMode() throws Exception {
        MockMvc mvc = mvc("unsafe-local");
        for (String host : new String[] {"evil.example.com", "evil.example.com:8080", "localhost.evil.com",
                "127.0.0.1.evil.com", "192.168.1.5:8080", "localhost:abc", "[::1]x"}) {
            mvc.perform(get("/ping").header("Host", host))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @Test
    void localHostNamesAreAccepted() throws Exception {
        MockMvc mvc = mvc("unsafe-local");
        for (String host : new String[] {"localhost", "localhost:8080", "127.0.0.1", "127.0.0.1:8080", "[::1]",
                "[::1]:8080", "LOCALHOST:3000"}) {
            mvc.perform(get("/ping").header("Host", host)).andExpect(status().isOk());
        }
    }

    @Test
    void otherModesAreNotFiltered() throws Exception {
        mvc("docker").perform(get("/ping").header("Host", "evil.example.com")).andExpect(status().isOk());
    }

    @Test
    void hostParsing() {
        assertTrue(HostHeaderFilter.isLocalHost("localhost:65535"));
        assertFalse(HostHeaderFilter.isLocalHost(null));
        assertFalse(HostHeaderFilter.isLocalHost("localhost:123456"));
    }
}
