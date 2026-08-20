package bg.softuni.partssvc.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReservationRequest(
        @NotNull(message = "The repair order reference is required")
        UUID repairOrderId,

        @NotBlank(message = "The part SKU is required")
        String sku,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "At least one unit must be reserved")
        @Max(value = 1000, message = "That quantity is unrealistically high")
        Integer quantity) {
}
