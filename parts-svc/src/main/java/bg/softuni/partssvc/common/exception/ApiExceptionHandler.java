package bg.softuni.partssvc.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    private static final String BASE_TYPE = "https://pistonworks.bg/problems/";

    @ExceptionHandler({PartNotFoundException.class, ReservationNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException exception, HttpServletRequest request) {
        log.warn("Not found on {}: {}", request.getRequestURI(), exception.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), "not-found");
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException exception) {
        log.warn("Rejected reservation: {}", exception.getMessage());

        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Insufficient stock",
                exception.getMessage(), "insufficient-stock");
        problem.setProperty("sku", exception.getSku());
        problem.setProperty("requested", exception.getRequested());
        problem.setProperty("available", exception.getAvailable());
        return problem;
    }

    @ExceptionHandler(ReservationStateException.class)
    public ProblemDetail handleReservationState(ReservationStateException exception) {
        log.warn("Rejected reservation transition: {}", exception.getMessage());
        return problem(HttpStatus.CONFLICT, "Reservation cannot change state",
                exception.getMessage(), "reservation-state");
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ProblemDetail handleDuplicateSku(DuplicateSkuException exception) {
        log.warn("Rejected duplicate SKU: {}", exception.getMessage());
        return problem(HttpStatus.CONFLICT, "Duplicate SKU", exception.getMessage(), "duplicate-sku");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        log.warn("Rejected invalid payload with {} field error(s)", errors.size());

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", "validation");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        log.warn("Rejected unreadable request body: {}", exception.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "The request body could not be parsed", "malformed-body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("Rejected malformed parameter '{}'", exception.getName());
        return problem(HttpStatus.BAD_REQUEST, "Malformed parameter",
                "The value supplied for '" + exception.getName() + "' is not valid", "malformed-parameter");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleUnknownPath(NoResourceFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Unknown endpoint",
                "No endpoint matches " + exception.getResourcePath(), "unknown-endpoint");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected failure on {}", request.getRequestURI(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "The parts service could not complete the request", "internal");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(BASE_TYPE + type));
        return problem;
    }
}
