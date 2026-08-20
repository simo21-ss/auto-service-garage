package bg.softuni.garage.parts.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PartView(UUID id,
                       String sku,
                       String name,
                       String category,
                       BigDecimal unitPrice,
                       int quantityOnHand,
                       int quantityReserved,
                       int quantityAvailable,
                       int reorderLevel,
                       boolean belowReorderLevel,
                       String supplierName) {
}
