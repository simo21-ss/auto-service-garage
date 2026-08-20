package bg.softuni.partssvc.part.dto;

import bg.softuni.partssvc.part.PartCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PartUpsertRequest(
        @NotBlank(message = "SKU is required")
        @Size(min = 3, max = 24, message = "SKU must be between 3 and 24 characters")
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "SKU may contain only capital letters, digits and dashes")
        String sku,

        @NotBlank(message = "Name is required")
        @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
        String name,

        @NotNull(message = "Category is required")
        PartCategory category,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.01", message = "Unit price must be greater than zero")
        BigDecimal unitPrice,

        @NotNull(message = "Opening stock is required")
        @Min(value = 0, message = "Opening stock cannot be negative")
        @Max(value = 100000, message = "Opening stock is unrealistically high")
        Integer quantityOnHand,

        @NotNull(message = "Reorder level is required")
        @Min(value = 0, message = "Reorder level cannot be negative")
        @Max(value = 10000, message = "Reorder level is unrealistically high")
        Integer reorderLevel,

        @NotNull(message = "Reorder quantity is required")
        @Min(value = 1, message = "Reorder quantity must be at least 1")
        @Max(value = 10000, message = "Reorder quantity is unrealistically high")
        Integer reorderQuantity,

        @NotBlank(message = "Supplier is required")
        String supplierName) {
}
