package com.immunization.validator.error;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns every exception raised while handling a request into the {@link ApiError} shape.
 *
 * Logging rule: log only codes and server-defined field names. Exception messages from
 * parsing and binding can quote what the client sent, so they stay out of the logs,
 * except for unexpected errors, where the stack trace is needed.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INTEGER = "integer";
    private static final int REJECTED_VALUE_MAX_CHARS = 10;

    // ─── Bad requests ─────────────────────────────────────────────────────────

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String name = ex.getParameterName();
        return reject(ex, headers, status, request,
                ErrorCode.MISSING_PARAMETER, name, ErrorMessages.missingParameter(name));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ex.getPropertyName();
        String expectedType = Integer.class.equals(ex.getRequiredType()) ? INTEGER : null;
        return reject(ex, headers, status, request,
                error(status, ErrorCode.INVALID_PARAMETER_TYPE, name, ErrorMessages.wrongType(name))
                        .expectedType(expectedType)
                        .build());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldProblem> problems = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldProblem(
                        ErrorMessages.jsonField(error.getField()), ErrorCode.VALIDATION_FAILED, error.getDefaultMessage()))
                .collect(Collectors.toList());
        return rejectFields(ex, headers, status, request, problems);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<ApiError.FieldProblem> problems = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldProblem(
                        lastNode(violation), ErrorCode.VALIDATION_FAILED, violation.getMessage()))
                .collect(Collectors.toList());
        return rejectFields(ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, request, problems);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // "age": "five" in a batch body is the same mistake as age=five on /single.
        if (ex.getCause() instanceof InvalidFormatException format && isTopLevelAge(format)) {
            return reject(ex, headers, status, request,
                    error(status, ErrorCode.INVALID_PARAMETER_TYPE, "age", ErrorMessages.AGE_WHOLE_NUMBER)
                            .expectedType(INTEGER)
                            .build());
        }
        ApiError.FieldProblem explanation =
                new ApiError.FieldProblem(null, ErrorCode.MALFORMED_REQUEST, explainUnreadable(ex));
        return reject(ex, headers, status, request,
                error(status, ErrorCode.MALFORMED_REQUEST, null, ErrorMessages.MALFORMED_REQUEST)
                        .details(List.of(explanation))
                        .build());
    }

    @ExceptionHandler(ApiRequestException.class)
    public ResponseEntity<Object> handleApiRequest(ApiRequestException ex, WebRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return reject(ex, new HttpHeaders(), status, request,
                error(status, ex.getErrorCode(), ex.getField(), ex.getMessage())
                        .rejectedValue(ClientText.shorten(ex.getRejectedValue(), REJECTED_VALUE_MAX_CHARS))
                        .build());
    }

    // ─── Wrong address, method or content type ────────────────────────────────

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return reject(ex, headers, status, request,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE, null, ErrorMessages.UNSUPPORTED_MEDIA_TYPE);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return reject(ex, headers, status, request,
                ErrorCode.METHOD_NOT_ALLOWED, null, ErrorMessages.METHOD_NOT_ALLOWED);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return reject(ex, headers, status, request,
                ErrorCode.NOT_FOUND, null, ErrorMessages.NOT_FOUND);
    }

    // ─── Everything else ──────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        return handleExceptionInternal(ex, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * Every response from the base class passes through here, including the exceptions not
     * overridden above, so none of them can leave in a different shape. Also forces JSON,
     * so the error still reaches a client whose Accept header rules JSON out.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Object errorBody = body instanceof ApiError ? body : fallbackBody(ex, status);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.addAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);

        return super.handleExceptionInternal(ex, errorBody, responseHeaders, status, request);
    }

    private ApiError fallbackBody(Exception ex, HttpStatusCode status) {
        if (status.is5xxServerError()) {
            log.error("Unexpected error while handling request", ex);
            return error(status, ErrorCode.INTERNAL_ERROR, null, ErrorMessages.INTERNAL_ERROR).build();
        }
        logRejection(status, ErrorCode.REQUEST_NOT_SUPPORTED, null, ex);
        return error(status, ErrorCode.REQUEST_NOT_SUPPORTED, null, ErrorMessages.REQUEST_NOT_SUPPORTED).build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<Object> reject(Exception ex, HttpHeaders headers, HttpStatusCode status,
                                          WebRequest request, ErrorCode code, String field, String message) {
        return reject(ex, headers, status, request, error(status, code, field, message).build());
    }

    private ResponseEntity<Object> reject(Exception ex, HttpHeaders headers, HttpStatusCode status,
                                          WebRequest request, ApiError body) {
        logRejection(status, body.getErrorCode(), body.getField(), ex);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private ResponseEntity<Object> rejectFields(Exception ex, HttpHeaders headers, HttpStatusCode status,
                                                WebRequest request, List<ApiError.FieldProblem> problems) {
        if (problems.isEmpty()) {
            return reject(ex, headers, status, request,
                    ErrorCode.VALIDATION_FAILED, null, ErrorMessages.INVALID_REQUEST);
        }
        // The validator reports failures in no fixed order; sort so "field" is predictable.
        List<ApiError.FieldProblem> sorted = problems.stream()
                .sorted(Comparator.comparing(ApiError.FieldProblem::getField))
                .collect(Collectors.toList());
        ApiError.FieldProblem first = sorted.get(0);

        return reject(ex, headers, status, request,
                error(status, ErrorCode.VALIDATION_FAILED, first.getField(), first.getMessage())
                        .details(sorted.size() > 1 ? sorted : null)
                        .build());
    }

    private ApiError.ApiErrorBuilder error(HttpStatusCode status, ErrorCode code, String field, String message) {
        return ApiError.builder()
                .httpStatus(status.value())
                .errorCode(code)
                .field(field)
                .message(message)
                .requestId(MDC.get(RequestIdFilter.MDC_KEY));
    }

    /**
     * A fixed phrase plus the parser's position. Never uses the exception's own message,
     * because that can quote the submitted content.
     */
    private String explainUnreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause == null) {
            return ErrorMessages.NO_RECORD + ".";
        }
        // A syntax error inside a list arrives wrapped in a mapping error, so check the whole chain.
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof JsonParseException parse) {
                return withPosition(ErrorMessages.NOT_VALID_JSON, parse.getLocation());
            }
        }
        JsonLocation location = cause instanceof JsonProcessingException processing ? processing.getLocation() : null;
        return withPosition(ErrorMessages.NOT_EXPECTED_FORMAT, location);
    }

    private String withPosition(String phrase, JsonLocation location) {
        if (location == null || location.getLineNr() < 1 || location.getColumnNr() < 1) {
            return phrase + ".";
        }
        return phrase + " (line " + location.getLineNr() + ", column " + location.getColumnNr() + ").";
    }

    private void logRejection(HttpStatusCode status, ErrorCode code, String field, Exception ex) {
        log.warn("Request rejected - HTTP {}, errorCode: {}, field: {}, cause: {}",
                status.value(), code, field, ex.getClass().getSimpleName());
    }

    private boolean isTopLevelAge(JsonMappingException ex) {
        List<JsonMappingException.Reference> path = ex.getPath();
        return path.size() == 1 && "age".equals(path.get(0).getFieldName());
    }

    private String lastNode(ConstraintViolation<?> violation) {
        String name = null;
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name != null ? name : "";
    }
}
