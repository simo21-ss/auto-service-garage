package bg.softuni.garage.common.validation;

import bg.softuni.garage.user.dto.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, RegisterRequest> {

    private static final String CONFIRM_FIELD = "confirmPassword";

    @Override
    public boolean isValid(RegisterRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        boolean matches = Objects.equals(request.getPassword(), request.getConfirmPassword());
        if (!matches) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(CONFIRM_FIELD)
                    .addConstraintViolation();
        }
        return matches;
    }
}
