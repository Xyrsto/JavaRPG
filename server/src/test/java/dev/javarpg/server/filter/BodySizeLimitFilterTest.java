package dev.javarpg.server.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.javarpg.server.config.JavaRpgProperties;
import dev.javarpg.server.exception.BodyTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** F-13: oversized request bodies are refused with 413 before deserialisation. */
class BodySizeLimitFilterTest {

    @RestController
    static class Echo {
        @PostMapping("/echo")
        String echo(@RequestBody String body) {
            return "len=" + body.length();
        }
    }

    private static MockMvc mvc(long limit) {
        JavaRpgProperties props = new JavaRpgProperties();
        props.getHttp().setMaxBodyBytes(limit);
        return MockMvcBuilders.standaloneSetup(new Echo()).addFilters(new BodySizeLimitFilter(props)).build();
    }

    @Test
    void defaultLimitIs64KiB() {
        assertEquals(65536L, new JavaRpgProperties().getHttp().getMaxBodyBytes());
    }

    @Test
    void declaredContentLengthOverTheLimitGets413() throws Exception {
        byte[] big = new byte[65537];
        Arrays.fill(big, (byte) 'a');
        mvc(65536).perform(post("/echo").contentType(MediaType.TEXT_PLAIN).content(big))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("SOURCE_TOO_LARGE"))
                .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    @Test
    void aBodyWithinTheLimitPasses() throws Exception {
        byte[] ok = new byte[65536];
        Arrays.fill(ok, (byte) 'a');
        mvc(65536).perform(post("/echo").contentType(MediaType.TEXT_PLAIN).content(ok))
                .andExpect(status().isOk());
    }

    /** A request that hides its length, like a chunked upload. */
    private static MockHttpServletRequest unknownLength(byte[] content) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/echo") {
            @Override
            public long getContentLengthLong() {
                return -1L;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContent(content);
        return request;
    }

    @Test
    void chunkedBodyIsCutOffWhenItPassesTheLimit() throws Exception {
        JavaRpgProperties props = new JavaRpgProperties();
        props.getHttp().setMaxBodyBytes(1000L);
        BodySizeLimitFilter filter = new BodySizeLimitFilter(props);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> req.getInputStream().readAllBytes();
        filter.doFilter(unknownLength(new byte[5000]), response, chain);
        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"error\""), response.getContentAsString());
    }

    @Test
    void countingStreamThrowsOnlyPastTheLimit() throws Exception {
        HttpServletRequest wrapped = new CountingRequest(unknownLength(new byte[100]), 100);
        assertEquals(100, wrapped.getInputStream().readAllBytes().length);
        HttpServletRequest over = new CountingRequest(unknownLength(new byte[101]), 100);
        assertThrows(BodyTooLargeException.class, () -> over.getInputStream().readAllBytes());
    }

    @Test
    void readerPathIsCountedToo() throws Exception {
        byte[] text = "x".repeat(200).getBytes(StandardCharsets.UTF_8);
        HttpServletRequest over = new CountingRequest(unknownLength(text), 100);
        assertThrows(BodyTooLargeException.class, () -> over.getReader().readLine());
    }
}
