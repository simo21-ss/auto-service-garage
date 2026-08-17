package bg.softuni.garage.repairorder;

import bg.softuni.garage.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RepairOrderRepository extends JpaRepository<RepairOrder, UUID> {

    List<RepairOrder> findAllByVehicleOwnerOrderByCreatedAtDesc(User owner);

    List<RepairOrder> findAllByStatusInOrderByCreatedAtAsc(List<RepairOrderStatus> statuses);

    List<RepairOrder> findAllByStatusOrderByCreatedAtDesc(RepairOrderStatus status);

    long countByStatus(RepairOrderStatus status);

    @Query("select o from RepairOrder o where o.status = :status and o.scheduledAt < :cutoff")
    List<RepairOrder> findOverdue(@Param("status") RepairOrderStatus status,
                                  @Param("cutoff") LocalDateTime cutoff);

    @Query("select count(o) from RepairOrder o where o.reference like concat(:prefix, '%')")
    long countByReferencePrefix(@Param("prefix") String prefix);
}
