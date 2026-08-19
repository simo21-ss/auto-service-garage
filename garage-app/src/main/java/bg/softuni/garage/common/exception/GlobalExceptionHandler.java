package bg.softuni.garage.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String ERROR_VIEW = "error";

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(ResourceNotFoundException exception, Model model) {
        log.warn("Resource not found: {}", exception.getMessage());
        return renderError(model, HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({DuplicateResourceException.class, BusinessRuleException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBusinessRule(RuntimeException exception, Model model) {
        log.warn("Business rule violated: {}", exception.getMessage());
        return renderError(model, HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAuthorizationDenied(AuthorizationDeniedException exception, Model model) {
        log.warn("Access denied: {}", exception.getMessage());
        return renderError(model, HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleTypeMismatch(MethodArgumentTypeMismatchException exception, Model model) {
        log.warn("Malformed request parameter '{}'", exception.getName());
        return renderError(model, HttpStatus.BAD_REQUEST,
                "The value supplied for '" + exception.getName() + "' is not valid.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleValidationFailure(MethodArgumentNotValidException exception, Model model) {
        log.warn("Submitted form failed validation with {} error(s)", exception.getErrorCount());
        return renderError(model, HttpStatus.BAD_REQUEST,
                "The submitted form contains invalid values.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleDataIntegrityViolation(DataIntegrityViolationException exception, Model model) {
        log.warn("Rejected an operation that would break data integrity: {}", exception.getMessage());
        return renderError(model, HttpStatus.CONFLICT,
                "That record is still referenced elsewhere and cannot be changed this way.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public String handleUnsupportedMethod(HttpRequestMethodNotSupportedException exception, Model model) {
        log.warn("Unsupported method {} for the requested page", exception.getMethod());
        return renderError(model, HttpStatus.METHOD_NOT_ALLOWED,
                "That action is not available for this page.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleUnknownPath(NoResourceFoundException exception, Model model) {
        log.warn("No handler for path {}", exception.getResourcePath());
        return renderError(model, HttpStatus.NOT_FOUND, "The requested page does not exist.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnexpected(Exception exception, Model model) {
        log.error("Unexpected failure while handling a request", exception);
        return renderError(model, HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. Please try again later.");
    }

    private String renderError(Model model, HttpStatus status, String message) {
        model.addAttribute("status", status.value());
        model.addAttribute("reason", status.getReasonPhrase());
        model.addAttribute("message", message);
        return ERROR_VIEW;
    }
}
