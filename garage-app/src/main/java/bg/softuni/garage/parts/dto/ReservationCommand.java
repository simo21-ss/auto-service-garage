package bg.softuni.garage.parts.dto;

import java.util.UUID;

public record ReservationCommand(UUID repairOrderId, String sku, int quantity) {
}
