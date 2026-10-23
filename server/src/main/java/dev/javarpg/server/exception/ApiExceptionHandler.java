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
        return respond(statusFor(e.code()), e.code().name(), e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErrorEnvelope> badRequest(Exception e) {
        if (BodyTooLargeException.isCausedBy(e)) {
            return respond(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.SOURCE_TOO_LARGE.name(),
                    "That request is too large. Send less data.");
        }
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.name(),
                "Something in your request was not understood. Check the values and try again.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.BAD_REQUEST.name(),
                "That action is not supported here.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> unsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.BAD_REQUEST.name(),
                "Send your request as JSON.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorEnvelope> notFound(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "There is no such address.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> unexpected(Exception e) {
        LOG.log(Level.SEVERE, "Unhandled error while serving a request", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL.name(), INTERNAL_MESSAGE);
    }

    static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case PLAYER_NOT_FOUND, QUEST_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case QUEST_LOCKED -> HttpStatus.FORBIDDEN;
            case QUEST_PROFILE_UNSUPPORTED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case SOURCE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case HINT_NOT_AVAILABLE, NO_MORE_HINTS, NO_NPC_ADJACENT -> HttpStatus.CONFLICT;
            case RUN_IN_PROGRESS -> HttpStatus.TOO_MANY_REQUESTS;
            case SERVER_BUSY -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static ResponseEntity<ErrorEnvelope> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(new ErrorBody(code, message)));
    }
}
