package bg.softuni.garage.repairorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceTaskRepository extends JpaRepository<ServiceTask, UUID> {

    List<ServiceTask> findAllByRepairOrderOrderByCreatedAtAsc(RepairOrder repairOrder);

    long countByRepairOrderAndStatus(RepairOrder repairOrder, ServiceTaskStatus status);

    void deleteAllByRepairOrder(RepairOrder repairOrder);
}
