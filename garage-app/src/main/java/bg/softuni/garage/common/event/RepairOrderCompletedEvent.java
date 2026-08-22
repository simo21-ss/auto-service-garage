package bg.softuni.garage.common.event;

import java.math.BigDecimal;
import java.util.UUID;

public record RepairOrderCompletedEvent(UUID orderId,
                                        String reference,
                                        String plate,
                                        String mechanicName,
                                        BigDecimal totalCost) {
}
