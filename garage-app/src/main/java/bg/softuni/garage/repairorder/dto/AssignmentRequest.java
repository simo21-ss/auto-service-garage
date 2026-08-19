package bg.softuni.garage.repairorder.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class AssignmentRequest {

    @NotNull(message = "Please choose a mechanic")
    private UUID mechanicId;

    @NotNull(message = "Please choose a slot")
    @Future(message = "The slot must be in the future")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime scheduledAt;
}
