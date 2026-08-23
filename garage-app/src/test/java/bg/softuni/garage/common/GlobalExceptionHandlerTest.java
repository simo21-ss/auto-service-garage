package bg.softuni.garage.common;

import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.DuplicateResourceException;
import bg.softuni.garage.common.exception.GlobalExceptionHandler;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void aMissingResourceRendersTheErrorViewWithA404() {
        Model model = new ConcurrentModel();

        String view = handler.handleResourceNotFound(new ResourceNotFoundException("Vehicle not found"), model);

        assertThat(view).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(model.getAttribute("message")).isEqualTo("Vehicle not found");
    }

    @Test
    void aBrokenBusinessRuleRendersA400() {
        Model model = new ConcurrentModel();

        handler.handleBusinessRule(new BusinessRuleException("Mileage cannot be lower"), model);

        assertThat(model.getAttribute("status")).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void aDuplicateIsAlsoTreatedAsABusinessRule() {
        Model model = new ConcurrentModel();

        handler.handleBusinessRule(new DuplicateResourceException("Plate already registered"), model);

        assertThat(model.getAttribute("message")).isEqualTo("Plate already registered");
    }

    @Test
    void aDeniedAuthorisationRendersA403WithoutLeakingInternals() {
        Model model = new ConcurrentModel();

        handler.handleAuthorizationDenied(new AuthorizationDeniedException("denied"), model);

        assertThat(model.getAttribute("status")).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(model.getAttribute("message")).asString().contains("do not have permission");
    }

    @Test
    void aMalformedParameterRendersA400NamingTheField() {
        Model model = new ConcurrentModel();
        MethodArgumentTypeMismatchException exception =
                new MethodArgumentTypeMismatchException("abc", java.util.UUID.class, "id", null, null);

        handler.handleTypeMismatch(exception, model);

        assertThat(model.getAttribute("message")).asString().contains("id");
    }

    @Test
    void anUnsupportedMethodRendersA405() {
        Model model = new ConcurrentModel();

        handler.handleUnsupportedMethod(new HttpRequestMethodNotSupportedException("POST"), model);

        assertThat(model.getAttribute("status")).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
    }

    @Test
    void aConstraintViolationRendersA409() {
        Model model = new ConcurrentModel();

        handler.handleDataIntegrityViolation(new DataIntegrityViolationException("fk"), model);

        assertThat(model.getAttribute("status")).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void anUnknownPathRendersA404() {
        Model model = new ConcurrentModel();

        handler.handleUnknownPath(
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/nope"), model);

        assertThat(model.getAttribute("status")).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void anythingUnexpectedStillRendersTheErrorViewRatherThanAWhitelabelPage() {
        Model model = new ConcurrentModel();

        String view = handler.handleUnexpected(new IllegalStateException("boom"), model);

        assertThat(view).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(model.getAttribute("message")).asString().doesNotContain("boom");
    }
}
