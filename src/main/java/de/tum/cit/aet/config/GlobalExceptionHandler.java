package de.tum.cit.aet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle any unhandled exceptions in the application.
     *
     * @param ex  The exception that was thrown.
     * @param req The web request during which the exception was thrown.
     * @return A generic internal server error response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAny(Exception ex, WebRequest req) {
        log.error(
            "Unhandled exception at {} {}",
            ((ServletWebRequest) req).getHttpMethod(),
            ((ServletWebRequest) req).getRequest().getRequestURI(),
            ex
        );
        return ResponseEntity.internalServerError().build();
    }

    @ExceptionHandler(
        {
            org.springframework.http.converter.HttpMessageNotWritableException.class,
            com.fasterxml.jackson.databind.exc.InvalidDefinitionException.class,
        }
    )
    public ResponseEntity<Object> handleSerialization(Exception ex, WebRequest req) {
        log.error("Serialization error: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError().build();
    }
}
