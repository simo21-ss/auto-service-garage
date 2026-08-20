package bg.softuni.partssvc.reservation;

import bg.softuni.partssvc.reservation.dto.ReservationRequest;
import bg.softuni.partssvc.reservation.dto.ReservationResponse;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface ReservationService {

    ReservationResponse reserve(ReservationRequest request, String actor);

    ReservationResponse consume(UUID reservationId, String actor);

    ReservationResponse release(UUID reservationId, String actor);

    ReservationResponse getById(UUID reservationId);

    List<ReservationResponse> findForRepairOrder(UUID repairOrderId);

    int expireStaleReservations(Duration olderThan, String actor);
}
