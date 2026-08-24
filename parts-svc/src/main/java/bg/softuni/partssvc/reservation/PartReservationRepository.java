package bg.softuni.partssvc.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PartReservationRepository extends JpaRepository<PartReservation, UUID> {

    List<PartReservation> findAllByRepairOrderIdOrderByCreatedAtAsc(UUID repairOrderId);

    List<PartReservation> findAllByStatusAndCreatedAtBefore(ReservationStatus status, LocalDateTime cutoff);

}
