package de.tum.cit.aet.web.rest.errors;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Translates exceptions thrown by the application into {@code application/problem+json} responses.
 *
 * <p>Replaces the previous catch-all handler that returned an empty {@code 500} for every error. The body is a flat
 * (RFC 7807-style) document with the JHipster fields ({@code message}, {@code fieldErrors}, {@code path}) at the top
 * level, which is what the Angular client's error interceptor reads. Common Spring MVC exceptions implement
 * {@link ErrorResponse} and carry their own status, so a single generic handler maps them; validation, concurrency,
 * authentication and access-denied get a dedicated mapping.
 */
@RestControllerAdvice
public class ExceptionTranslator {

    private final Logger log = LoggerFactory.getLogger(ExceptionTranslator.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, NativeWebRequest request) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream().map(this::toFieldError).toList();
        Map<String, Object> body = body(HttpStatus.BAD_REQUEST, ErrorConstants.ERR_VALIDATION, null, null, request);
        body.put("fieldErrors", fieldErrors);
        return response(HttpStatus.BAD_REQUEST, body);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrencyFailure(ConcurrencyFailureException ex, NativeWebRequest request) {
        return response(HttpStatus.CONFLICT, body(HttpStatus.CONFLICT, ErrorConstants.ERR_CONCURRENCY_FAILURE, null, null, request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, NativeWebRequest request) {
        return response(HttpStatus.FORBIDDEN, body(HttpStatus.FORBIDDEN, errorCode(HttpStatus.FORBIDDEN), ex.getMessage(), null, request));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex, NativeWebRequest request) {
        return response(
            HttpStatus.UNAUTHORIZED,
            body(HttpStatus.UNAUTHORIZED, errorCode(HttpStatus.UNAUTHORIZED), ex.getMessage(), null, request)
        );
    }

    /**
     * Fallback handler for any otherwise-unmapped exception. Uses the status carried by Spring MVC's
     * {@link ErrorResponse} exceptions or a {@link ResponseStatus} annotation, and defaults to {@code 500}.
     *
     * @param ex      the exception that was thrown
     * @param request the current web request
     * @return a {@code problem+json} response with the resolved status
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, Object>> handleAny(Throwable ex, NativeWebRequest request) {
        HttpStatusCode status;
        String title = null;
        String detail = ex.getMessage();
        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        if (ex instanceof ErrorResponse errorResponse) {
            status = errorResponse.getStatusCode();
        } else if (responseStatus != null) {
            status = responseStatus.code();
            if (StringUtils.hasText(responseStatus.reason())) {
                title = responseStatus.reason();
            }
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            detail = null;
        }
        if (status.is5xxServerError()) {
            log.error("Unhandled exception at {}: {}", path(request), ex.getMessage(), ex);
        }
        return response(status, body(status, errorCode(status), detail, title, request));
    }

    private Map<String, String> toFieldError(FieldError error) {
        String objectName = error.getObjectName().replaceFirst("DTO$", "").replaceFirst("VM$", "");
        return Map.of(
            "objectName",
            objectName,
            "field",
            error.getField(),
            "message",
            error.getDefaultMessage() == null ? "" : error.getDefaultMessage()
        );
    }

    private Map<String, Object> body(HttpStatusCode status, String message, String detail, String title, NativeWebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title != null ? title : reasonPhrase(status));
        body.put("status", status.value());
        if (detail != null) {
            body.put("detail", detail);
        }
        body.put("path", path(request));
        body.put("message", message);
        return body;
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatusCode status, Map<String, Object> body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private static String errorCode(HttpStatusCode status) {
        return "error.http." + status.value();
    }

    private static String reasonPhrase(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved == null ? "" : resolved.getReasonPhrase();
    }

    private String path(NativeWebRequest request) {
        HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
        return servletRequest == null ? null : servletRequest.getRequestURI();
    }
}
