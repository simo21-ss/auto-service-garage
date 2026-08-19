package bg.softuni.garage.repairorder;

import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import bg.softuni.garage.repairorder.dto.ServiceTaskRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ServiceTaskServiceImpl implements ServiceTaskService {

    private final ServiceTaskRepository serviceTaskRepository;
    private final RepairOrderRepository repairOrderRepository;

    public ServiceTaskServiceImpl(ServiceTaskRepository serviceTaskRepository,
                                  RepairOrderRepository repairOrderRepository) {
        this.serviceTaskRepository = serviceTaskRepository;
        this.repairOrderRepository = repairOrderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceTask> findForOrder(RepairOrder repairOrder) {
        return serviceTaskRepository.findAllByRepairOrderOrderByCreatedAtAsc(repairOrder);
    }

    @Override
    @Transactional
    public ServiceTask add(UUID orderId, ServiceTaskRequest request) {
        RepairOrder order = loadOrder(orderId);
        if (order.getMechanic() == null) {
            throw new BusinessRuleException("Assign a mechanic before adding service tasks");
        }
        if (order.getStatus() != RepairOrderStatus.SCHEDULED
                && order.getStatus() != RepairOrderStatus.IN_PROGRESS) {
            throw new BusinessRuleException(
                    "Service tasks can only be added to scheduled or in-progress orders");
        }

        ServiceTask task = new ServiceTask();
        task.setRepairOrder(order);
        task.setOperation(request.getOperation().trim());
        task.setHours(request.getHours());
        task.setHourlyRate(order.getMechanic().getHourlyRate());
        task.setStatus(ServiceTaskStatus.PENDING);
        task.setCreatedAt(LocalDateTime.now());

        ServiceTask saved = serviceTaskRepository.save(task);
        order.setStatus(RepairOrderStatus.IN_PROGRESS);
        recalculateLabour(order);

        log.info("Added task '{}' ({} h) to repair order {}",
                saved.getOperation(), saved.getHours(), order.getReference());
        return saved;
    }

    @Override
    @Transactional
    public ServiceTask complete(UUID orderId, UUID taskId) {
        RepairOrder order = loadOrder(orderId);
        ServiceTask task = loadTask(taskId, order);

        if (task.getStatus() == ServiceTaskStatus.DONE) {
            throw new BusinessRuleException("That task is already marked as done");
        }

        task.setStatus(ServiceTaskStatus.DONE);
        task.setCompletedAt(LocalDateTime.now());

        ServiceTask saved = serviceTaskRepository.save(task);
        log.info("Completed task '{}' on repair order {}", saved.getOperation(), order.getReference());
        return saved;
    }

    @Override
    @Transactional
    public void remove(UUID orderId, UUID taskId) {
        RepairOrder order = loadOrder(orderId);
        ServiceTask task = loadTask(taskId, order);

        if (task.getStatus() == ServiceTaskStatus.DONE) {
            throw new BusinessRuleException("Completed work cannot be removed from the order");
        }

        serviceTaskRepository.delete(task);
        recalculateLabour(order);
        log.info("Removed task '{}' from repair order {}", task.getOperation(), order.getReference());
    }

    private void recalculateLabour(RepairOrder order) {
        BigDecimal labour = serviceTaskRepository.findAllByRepairOrderOrderByCreatedAtAsc(order).stream()
                .map(task -> task.getHours().multiply(task.getHourlyRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setLabourCost(labour);
        repairOrderRepository.save(order);
    }

    private RepairOrder loadOrder(UUID orderId) {
        return repairOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Repair order not found"));
    }

    private ServiceTask loadTask(UUID taskId, RepairOrder order) {
        ServiceTask task = serviceTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Service task not found"));

        if (!task.getRepairOrder().getId().equals(order.getId())) {
            throw new ResourceNotFoundException("Service task not found");
        }
        return task;
    }
}
