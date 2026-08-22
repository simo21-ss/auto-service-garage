package bg.softuni.garage.common.event;

import java.util.UUID;

public record RepairOrderCancelledEvent(UUID orderId,
                                        String reference,
                                        String plate,
                                        int releasedReservations,
                                        boolean cancelledByStaff) {
}
