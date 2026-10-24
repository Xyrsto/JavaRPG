package dev.javarpg.server.docs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** FR-011: when documentation is switched off, its addresses look like any unknown address; the game still works. */
@SpringBootTest(properties = "javarpg.docs.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocsDisabledTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void documentationAddressesAnswerNotFound() throws Exception {
        for (String address : new String[] {"/v3/api-docs", "/v3/api-docs.yaml", "/docs", "/swagger-ui/index.html"}) {
            mvc.perform(get(address)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
    }

    @Test
    void theGameApiIsUnaffected() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/v1/world")).andExpect(status().isOk());
    }
}
