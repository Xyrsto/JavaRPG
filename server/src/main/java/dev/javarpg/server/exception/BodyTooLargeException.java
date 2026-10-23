package dev.javarpg.server.exception;

import java.io.IOException;

/** Thrown from the request stream once the body exceeds javarpg.http.max-body-bytes. */
public class BodyTooLargeException extends IOException {

    public BodyTooLargeException(long limit) {
        super("Request body is larger than " + limit + " bytes");
    }

    /** True when the throwable or any cause is a BodyTooLargeException. */
    public static boolean isCausedBy(Throwable t) {
        int depth = 0;
        for (Throwable c = t; c != null && depth < 10; c = c.getCause(), depth++) {
            if (c instanceof BodyTooLargeException) {
                return true;
            }
        }
        return false;
    }
}
