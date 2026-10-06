package com.educationerp.common.error;

import com.educationerp.common.web.RequestContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates every exception into a stable {@link ErrorResponse}. Raw stack traces and
 * developer-oriented messages are logged but never serialised to the client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleApp(AppException ex, HttpServletRequest request) {
        log.warn("Business error [{}] {} {} -> {}", ex.getCode(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(ex.getCode(), ex.getUserMessage(), ex.getFieldErrors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), humanise(fe));
        }
        for (var oe : ex.getBindingResult().getGlobalErrors()) {
            fieldErrors.putIfAbsent(oe.getObjectName(), oe.getDefaultMessage());
        }
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), fieldErrors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String key = v.getPropertyPath().toString();
            int idx = key.lastIndexOf('.');
            if (idx >= 0) {
                key = key.substring(idx + 1);
            }
            fieldErrors.putIfAbsent(key, v.getMessage());
        }
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), fieldErrors, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(ErrorCode.MISSING_PARAMETER, ErrorCode.MISSING_PARAMETER.defaultMessage(),
                Map.of(ex.getParameterName(), "This value is required."), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(ErrorCode.TYPE_MISMATCH, ErrorCode.TYPE_MISMATCH.defaultMessage(),
                Map.of(ex.getName(), "This value has an unexpected format."), request);
    }

    /**
     * A value this side does not recognise — almost always an enum typed by hand in a form —
     * is one wrong field, not a body Spring could not parse. Say which field and what the
     * choices are, so the screen can point at it instead of blaming the whole request.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        InvalidFormatException unknown = findCause(ex, InvalidFormatException.class);
        if (unknown != null && unknown.getTargetType() != null && unknown.getTargetType().isEnum()) {
            Map<String, String> fieldErrors = Map.of(fieldOf(unknown), choicesFor(unknown.getTargetType()));
            return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    fieldErrors, request);
        }
        log.debug("Unreadable body on {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.MALFORMED_JSON, ErrorCode.MALFORMED_JSON.defaultMessage(), null, request);
    }

    private <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

    private String fieldOf(InvalidFormatException ex) {
        List<JsonMappingException.Reference> path = ex.getPath();
        if (path.isEmpty()) {
            return "value";
        }
        String name = path.get(path.size() - 1).getFieldName();
        return name == null ? "value" : name;
    }

    private String choicesFor(Class<?> type) {
        return "Choose one of: " + Arrays.stream(type.getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
                .collect(Collectors.joining(", ")) + ".";
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> handleUpload(Exception ex, HttpServletRequest request) {
        ErrorCode code = ex instanceof MaxUploadSizeExceededException ? ErrorCode.FILE_TOO_LARGE : ErrorCode.BAD_REQUEST;
        return build(code, code.defaultMessage(), null, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(ErrorCode.OPTIMISTIC_LOCK_FAILURE, ErrorCode.OPTIMISTIC_LOCK_FAILURE.defaultMessage(), null, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.DUPLICATE_RESOURCE,
                "A record with these details already exists or conflicts with existing data.", null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.defaultMessage(), null, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(ErrorCode.AUTHENTICATION_REQUIRED, ErrorCode.AUTHENTICATION_REQUIRED.defaultMessage(), null, request);
    }

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, NoHandlerFoundException.class,
            org.springframework.web.servlet.resource.NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFoundRoute(Exception ex, HttpServletRequest request) {
        if (ex instanceof NoHandlerFoundException || ex instanceof org.springframework.web.servlet.resource.NoResourceFoundException) {
            return build(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.defaultMessage(), null, request);
        }
        ErrorCode code = ErrorCode.BAD_REQUEST;
        return build(code, "This action is not available for the requested method.", null, request);
    }

    /**
     * A sort or page parameter naming a property the entity does not have is a bad
     * request, not a server fault: without this it surfaced as an opaque 500.
     */
    @ExceptionHandler({PropertyReferenceException.class, InvalidDataAccessApiUsageException.class})
    public ResponseEntity<ErrorResponse> handleBadQuery(Exception ex, HttpServletRequest request) {
        log.warn("Rejected unusable query on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage());
        return build(ErrorCode.VALIDATION_ERROR,
                "That sort or paging request cannot be applied to this list.", null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {} (requestId={})", request.getMethod(), request.getRequestURI(),
                RequestContext.getRequestId(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), null, request);
    }

    private String humanise(FieldError fe) {
        String msg = fe.getDefaultMessage();
        if (msg == null || msg.isBlank()) {
            return "This value is not valid.";
        }
        if (fe.getRejectedValue() != null && msg.contains("{")) {
            // never leak rejected payload content verbatim
            return "This value is not valid.";
        }
        return msg;
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, Map<String, String> fieldErrors,
                                                HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                code.httpStatus(),
                code.name(),
                message,
                fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors,
                request.getRequestURI(),
                RequestContext.getRequestId());
        HttpStatus status = HttpStatus.valueOf(code.httpStatus());
        return ResponseEntity.status(status).body(body);
    }
}
