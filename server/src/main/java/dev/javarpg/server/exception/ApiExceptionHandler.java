package dev.javarpg.server.exception;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.core.contract.GameException;
import dev.javarpg.server.dto.ApiDtos.ErrorBody;
import dev.javarpg.server.dto.ApiDtos.ErrorEnvelope;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Every error leaves as {"error":{"code","message"}}. Stack traces go to the log only. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = Logger.getLogger(ApiExceptionHandler.class.getName());
    private static final String INTERNAL_MESSAGE = "Something went wrong on our side. Your progress is safe.";

    @ExceptionHandler(GameException.class)
    public ResponseEntity<ErrorEnvelope> gameError(GameException e) {
        return respond(e.code(), e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErrorEnvelope> badRequest(Exception e) {
        if (BodyTooLargeException.isCausedBy(e)) {
            return respond(ErrorCode.SOURCE_TOO_LARGE,
                    "That request is too large. Send less data.");
        }
        return respond(ErrorCode.BAD_REQUEST,
                "Something in your request was not understood. Check the values and try again.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.BAD_REQUEST,
                "That action is not supported here.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> unsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.BAD_REQUEST,
                "Send your request as JSON.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorEnvelope> notFound(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND, "There is no such address.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> unexpected(Exception e) {
        LOG.log(Level.SEVERE, "Unhandled error while serving a request", e);
        return respond(ErrorCode.INTERNAL, INTERNAL_MESSAGE);
    }

    private static ResponseEntity<ErrorEnvelope> respond(ErrorCode code, String message) {
        return respond(ErrorStatus.of(code), code, message);
    }

    /** For the few answers whose HTTP status is more specific than the status of their code (405 and 415 use BAD_REQUEST). */
    private static ResponseEntity<ErrorEnvelope> respond(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(new ErrorBody(code.name(), message)));
    }
}
