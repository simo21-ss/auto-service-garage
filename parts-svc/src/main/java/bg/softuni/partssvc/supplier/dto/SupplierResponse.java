package bg.softuni.partssvc.supplier.dto;

import java.util.UUID;

public record SupplierResponse(UUID id,
                               String name,
                               String email,
                               int leadTimeDays,
                               boolean active) {
}
