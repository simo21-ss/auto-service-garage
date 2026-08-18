package bg.softuni.garage.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    @Size(max = 120, message = "Email must be at most 120 characters")
    private String email;

    @NotBlank(message = "First name is required")
    @Size(max = 60, message = "First name must be at most 60 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 60, message = "Last name must be at most 60 characters")
    private String lastName;

    @Pattern(regexp = "^$|^[+0-9 ()-]{6,30}$", message = "Please enter a valid phone number")
    private String phone;
}
