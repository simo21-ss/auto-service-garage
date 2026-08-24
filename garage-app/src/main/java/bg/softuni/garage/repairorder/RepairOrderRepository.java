package bg.softuni.garage.repairorder;

import bg.softuni.garage.mechanic.Mechanic;
import bg.softuni.garage.user.User;
import bg.softuni.garage.vehicle.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RepairOrderRepository extends JpaRepository<RepairOrder, UUID> {

    List<RepairOrder> findAllByVehicleOwnerOrderByCreatedAtDesc(User owner);

    @Query("select o from RepairOrder o join fetch o.vehicle vehicle join fetch vehicle.owner "
            + "where o.status in :statuses order by o.createdAt asc")
    List<RepairOrder> findOpenWithOwners(@Param("statuses") Collection<RepairOrderStatus> statuses);

    List<RepairOrder> findAllByStatusOrderByCreatedAtDesc(RepairOrderStatus status);

    long countByStatus(RepairOrderStatus status);

    long countByStatusIn(Collection<RepairOrderStatus> statuses);

    boolean existsByVehicle(Vehicle vehicle);

    boolean existsByVehicleAndStatusIn(Vehicle vehicle, Collection<RepairOrderStatus> statuses);

    boolean existsByMechanic(Mechanic mechanic);

    boolean existsByMechanicAndStatusIn(Mechanic mechanic, Collection<RepairOrderStatus> statuses);

    @Query("select o from RepairOrder o where o.status = :status and o.scheduledAt < :cutoff")
    List<RepairOrder> findOverdue(@Param("status") RepairOrderStatus status,
                                  @Param("cutoff") LocalDateTime cutoff);
}
