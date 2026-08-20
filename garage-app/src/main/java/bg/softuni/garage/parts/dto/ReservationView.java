package bg.softuni.garage.parts.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationView(UUID id,
                              UUID repairOrderId,
                              String sku,
                              String partName,
                              int quantity,
                              BigDecimal unitPrice,
                              BigDecimal lineTotal,
                              String status,
                              LocalDateTime createdAt,
                              LocalDateTime resolvedAt) {
}
