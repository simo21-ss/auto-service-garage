package bg.softuni.garage.vehicle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VehicleRequest {

    @NotBlank(message = "Registration plate is required")
    @Size(min = 4, max = 12, message = "Plate must be between 4 and 12 characters")
    @Pattern(regexp = "^[A-Za-z0-9 -]+$",
            message = "Plate may contain only letters, digits, spaces and dashes")
    private String plate;

    @NotBlank(message = "VIN is required")
    @Size(min = 11, max = 17, message = "VIN must be between 11 and 17 characters")
    @Pattern(regexp = "^[A-HJ-NPR-Za-hj-npr-z0-9]+$",
            message = "VIN may not contain the letters I, O or Q")
    private String vin;

    @NotBlank(message = "Make is required")
    @Size(max = 40, message = "Make must be at most 40 characters")
    private String make;

    @NotBlank(message = "Model is required")
    @Size(max = 40, message = "Model must be at most 40 characters")
    private String model;

    @NotNull(message = "Model year is required")
    @Min(value = 1950, message = "Model year must be 1950 or later")
    @Max(value = 2100, message = "Model year is not valid")
    private Integer modelYear;

    @NotNull(message = "Mileage is required")
    @Min(value = 0, message = "Mileage cannot be negative")
    @Max(value = 2000000, message = "Mileage is not realistic")
    private Integer mileage;
}
