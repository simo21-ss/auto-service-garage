package bg.softuni.partssvc.part.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RestockRequest(
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Restock quantity must be at least 1")
        @Max(value = 10000, message = "Restock quantity is unrealistically high")
        Integer quantity,

        @Size(max = 120, message = "The note must be at most 120 characters")
        String note) {
}
