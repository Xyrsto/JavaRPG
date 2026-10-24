package dev.javarpg.server.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.javarpg.server.support.TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** F-14: when the player table is at its cap, creation answers 503 with the generic envelope. */
@SpringBootTest(properties = "javarpg.players.max=0")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlayersFullApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void creationReturns503WhenThePlayerTableIsFull() throws Exception {
        TestSupport.postEmpty(mvc, "/players")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SERVER_BUSY"))
                .andExpect(jsonPath("$.error.message").isNotEmpty());
    }
}
