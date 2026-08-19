package bg.softuni.garage.repairorder.dto;

import bg.softuni.garage.mechanic.Specialty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RepairOrderRequest {

    @NotNull(message = "Please choose a vehicle")
    private UUID vehicleId;

    @NotNull(message = "Please choose the type of work needed")
    private Specialty requiredSpecialty;

    @NotBlank(message = "Please describe the problem")
    @Size(min = 10, max = 1000, message = "The description must be between 10 and 1000 characters")
    private String complaint;
}
