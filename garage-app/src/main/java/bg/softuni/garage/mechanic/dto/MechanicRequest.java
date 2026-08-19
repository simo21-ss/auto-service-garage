package bg.softuni.garage.mechanic.dto;

import bg.softuni.garage.mechanic.Specialty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class MechanicRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 80, message = "Full name must be between 3 and 80 characters")
    private String fullName;

    @NotNull(message = "Please choose a specialty")
    private Specialty specialty;

    @NotNull(message = "Hourly rate is required")
    @DecimalMin(value = "10.00", message = "Hourly rate must be at least 10.00")
    @DecimalMax(value = "500.00", message = "Hourly rate must be at most 500.00")
    private BigDecimal hourlyRate;

    @NotNull(message = "Hire date is required")
    @PastOrPresent(message = "Hire date cannot be in the future")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hiredOn;

    private boolean active = true;
}
