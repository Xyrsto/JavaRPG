package dev.javarpg.server.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** FR-005: without the packaged client the root address explains what to do instead of failing. */
@SpringBootTest(properties = "javarpg.web.index=classpath:/test-web/does-not-exist.html")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RootControllerNotBuiltTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theRootAddressExplainsThatTheClientIsNotBuilt() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(Matchers.containsString("not built")))
                .andExpect(content().string(Matchers.containsString("-Pweb")))
                .andExpect(content().string(Matchers.containsString("href=\"/docs\"")))
                .andExpect(content().string(Matchers.containsString("https://xyrsto.github.io/JavaRPG/")));
    }
}
