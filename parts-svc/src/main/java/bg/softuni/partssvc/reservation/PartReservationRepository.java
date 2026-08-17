package bg.softuni.partssvc.reservation;

import bg.softuni.partssvc.part.Part;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PartReservationRepository extends JpaRepository<PartReservation, UUID> {

    List<PartReservation> findAllByRepairOrderIdOrderByCreatedAtAsc(UUID repairOrderId);

    List<PartReservation> findAllByRepairOrderIdAndStatus(UUID repairOrderId, ReservationStatus status);

    List<PartReservation> findAllByStatusAndCreatedAtBefore(ReservationStatus status, LocalDateTime cutoff);

    boolean existsByPartAndStatus(Part part, ReservationStatus status);
}
