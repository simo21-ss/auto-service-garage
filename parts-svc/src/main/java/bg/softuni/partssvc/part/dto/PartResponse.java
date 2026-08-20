package bg.softuni.partssvc.part.dto;

import bg.softuni.partssvc.part.PartCategory;

import java.math.BigDecimal;
import java.util.UUID;

public record PartResponse(UUID id,
                           String sku,
                           String name,
                           PartCategory category,
                           BigDecimal unitPrice,
                           int quantityOnHand,
                           int quantityReserved,
                           int quantityAvailable,
                           int reorderLevel,
                           boolean belowReorderLevel,
                           String supplierName) {
}
