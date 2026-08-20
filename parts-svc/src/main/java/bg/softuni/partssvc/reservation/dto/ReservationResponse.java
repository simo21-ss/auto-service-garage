package bg.softuni.partssvc.reservation.dto;

import bg.softuni.partssvc.reservation.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationResponse(UUID id,
                                  UUID repairOrderId,
                                  String sku,
                                  String partName,
                                  int quantity,
                                  BigDecimal unitPrice,
                                  BigDecimal lineTotal,
                                  ReservationStatus status,
                                  LocalDateTime createdAt,
                                  LocalDateTime resolvedAt) {
}
