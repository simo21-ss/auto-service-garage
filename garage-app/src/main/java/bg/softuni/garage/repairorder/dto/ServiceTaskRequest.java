package bg.softuni.garage.repairorder.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ServiceTaskRequest {

    @NotBlank(message = "Describe the operation")
    @Size(min = 3, max = 120, message = "The operation must be between 3 and 120 characters")
    private String operation;

    @NotNull(message = "Estimated hours are required")
    @DecimalMin(value = "0.25", message = "A task must take at least 0.25 hours")
    @DecimalMax(value = "40.00", message = "A single task cannot exceed 40 hours")
    private BigDecimal hours;
}
