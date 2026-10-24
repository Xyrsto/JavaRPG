package dev.javarpg.server.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** FR-004: when the client is packaged, the root address serves it and the page itself is never cached for long. */
@SpringBootTest(properties = "javarpg.web.index=classpath:/test-web/index.html")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RootControllerBuiltTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theRootAddressServesTheClientPageWithoutCaching() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(Matchers.containsString("fixture index")))
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-cache")));
    }
}
