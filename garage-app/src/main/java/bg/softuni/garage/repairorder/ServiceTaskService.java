package bg.softuni.garage.repairorder;

import bg.softuni.garage.repairorder.dto.ServiceTaskRequest;

import java.util.List;
import java.util.UUID;

public interface ServiceTaskService {

    List<ServiceTask> findForOrder(RepairOrder repairOrder);

    ServiceTask add(UUID orderId, ServiceTaskRequest request);

    ServiceTask complete(UUID orderId, UUID taskId);

    void remove(UUID orderId, UUID taskId);
}
