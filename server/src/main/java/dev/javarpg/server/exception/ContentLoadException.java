package dev.javarpg.server.exception;

/** Thrown at startup when a content file is invalid. The message names the file and the field. */
public class ContentLoadException extends RuntimeException {

    public ContentLoadException(String message) {
        super(message);
    }

    public ContentLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
