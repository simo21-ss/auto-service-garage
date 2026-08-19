package bg.softuni.garage.repairorder;

import bg.softuni.garage.repairorder.dto.AssignmentRequest;
import bg.softuni.garage.repairorder.dto.RepairOrderRequest;

import java.util.List;
import java.util.UUID;

public interface RepairOrderService {

    List<RepairOrder> findForCustomer(UUID customerId);

    List<RepairOrder> findOpenOrders();

    List<RepairOrder> findByStatus(RepairOrderStatus status);

    RepairOrder getById(UUID orderId);

    RepairOrder getForViewer(UUID orderId, UUID viewerId, boolean staffView);

    RepairOrder book(RepairOrderRequest request, UUID customerId);

    RepairOrder assign(UUID orderId, AssignmentRequest request);

    RepairOrder complete(UUID orderId);

    RepairOrder cancel(UUID orderId, UUID viewerId, boolean staffView);

    long countByStatus(RepairOrderStatus status);

    long countOpen();
}
