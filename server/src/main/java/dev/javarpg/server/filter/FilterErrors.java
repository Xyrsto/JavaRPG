package dev.javarpg.server.filter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Writes the generic {"error":{"code","message"}} envelope from servlet filters. Texts must not contain quotes. */
final class FilterErrors {

    private FilterErrors() {
    }

    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String json = "{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}";
        response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
    }
}
