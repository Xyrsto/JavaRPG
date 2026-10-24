package dev.javarpg.server.filter;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.server.exception.ErrorStatus;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the generic {"error":{"code","message"}} envelope from servlet filters, which run before Jackson. The
 * JSON is built by hand, so a message must not contain a quote or a backslash; that is checked.
 */
final class FilterErrors {

    private FilterErrors() {
    }

    static void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        if (message.indexOf('"') >= 0 || message.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Filter error messages must not contain quotes or backslashes");
        }
        response.setStatus(ErrorStatus.of(code).value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String json = "{\"error\":{\"code\":\"" + code.name() + "\",\"message\":\"" + message + "\"}}";
        response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
    }
}
