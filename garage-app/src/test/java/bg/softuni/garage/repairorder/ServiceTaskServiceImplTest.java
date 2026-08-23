package bg.softuni.garage.repairorder;

import bg.softuni.garage.TestFixtures;
import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import bg.softuni.garage.mechanic.Specialty;
import bg.softuni.garage.repairorder.dto.ServiceTaskRequest;
import bg.softuni.garage.user.RoleName;
import bg.softuni.garage.user.User;
import bg.softuni.garage.vehicle.Vehicle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTaskServiceImplTest {

    @Mock
    private ServiceTaskRepository serviceTaskRepository;

    @Mock
    private RepairOrderRepository repairOrderRepository;

    @InjectMocks
    private ServiceTaskServiceImpl serviceTaskService;

    @Test
    void addingATaskMovesTheOrderIntoProgressAndPricesTheLabour() {
        RepairOrder order = scheduledOrder();
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(serviceTaskRepository.save(any(ServiceTask.class))).thenAnswer(call -> call.getArgument(0));
        when(serviceTaskRepository.findAllByRepairOrderOrderByCreatedAtAsc(order))
                .thenReturn(List.of(TestFixtures.task(order, ServiceTaskStatus.PENDING, "2.5")));

        ServiceTask added = serviceTaskService.add(order.getId(), request("Replace discs", "2.5"));

        assertThat(added.getStatus()).isEqualTo(ServiceTaskStatus.PENDING);
        assertThat(added.getHourlyRate()).isEqualByComparingTo("55.00");
        assertThat(order.getStatus()).isEqualTo(RepairOrderStatus.IN_PROGRESS);

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(repairOrderRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getLabourCost()).isEqualByComparingTo("137.50");
    }

    @Test
    void aTaskCannotBeAddedBeforeAMechanicIsAssigned() {
        RepairOrder order = scheduledOrder();
        order.setMechanic(null);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> serviceTaskService.add(order.getId(), request("Replace discs", "2.5")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Assign a mechanic");
    }

    @Test
    void aTaskCannotBeAddedToACompletedOrder() {
        RepairOrder order = scheduledOrder();
        order.setStatus(RepairOrderStatus.COMPLETED);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> serviceTaskService.add(order.getId(), request("Replace discs", "2.5")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("scheduled or in-progress");
    }

    @Test
    void addingToAnUnknownOrderThrows() {
        UUID id = UUID.randomUUID();
        when(repairOrderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceTaskService.add(id, request("Replace discs", "2.5")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void completingATaskStampsTheCompletionTime() {
        RepairOrder order = scheduledOrder();
        ServiceTask task = TestFixtures.task(order, ServiceTaskStatus.PENDING, "2.0");
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(serviceTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(serviceTaskRepository.save(any(ServiceTask.class))).thenAnswer(call -> call.getArgument(0));

        ServiceTask completed = serviceTaskService.complete(order.getId(), task.getId());

        assertThat(completed.getStatus()).isEqualTo(ServiceTaskStatus.DONE);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void completingAnAlreadyDoneTaskIsRejected() {
        RepairOrder order = scheduledOrder();
        ServiceTask task = TestFixtures.task(order, ServiceTaskStatus.DONE, "2.0");
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(serviceTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> serviceTaskService.complete(order.getId(), task.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already marked as done");
    }

    @Test
    void aTaskFromAnotherOrderIsNotVisible() {
        RepairOrder order = scheduledOrder();
        RepairOrder otherOrder = scheduledOrder();
        ServiceTask task = TestFixtures.task(otherOrder, ServiceTaskStatus.PENDING, "2.0");
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(serviceTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> serviceTaskService.complete(order.getId(), task.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anUnknownTaskThrows() {
        RepairOrder order = scheduledOrder();
        UUID taskId = UUID.randomUUID();
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(serviceTaskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceTaskService.complete(order.getId(), taskId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removingAPendingTaskRecalculatesTheLabour() {
        RepairOrder order = scheduledOrder();
        ServiceTask task = TestFixtures.task(order, ServiceTaskStatus.PENDING, "2.0");
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(serviceTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(serviceTaskRepository.findAllByRepairOrderOrderByCreatedAtAsc(order)).thenReturn(List.of());

        serviceTaskService.remove(order.getId(), task.getId());

        verify(serviceTaskRepository).delete(task);
        assertThat(order.getLabourCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void completedWorkCannotBeRemoved() {
        RepairOrder order = scheduledOrder();
        ServiceTask task = TestFixtures.task(order, ServiceTaskStatus.DONE, "2.0");
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(serviceTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> serviceTaskService.remove(order.getId(), task.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Completed work");

        verify(serviceTaskRepository, never()).delete(any());
    }

    @Test
    void findForOrderDelegatesToTheRepository() {
        RepairOrder order = scheduledOrder();
        when(serviceTaskRepository.findAllByRepairOrderOrderByCreatedAtAsc(order))
                .thenReturn(List.of(TestFixtures.task(order, ServiceTaskStatus.PENDING, "1.0")));

        assertThat(serviceTaskService.findForOrder(order)).hasSize(1);
    }

    private RepairOrder scheduledOrder() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        RepairOrder order = TestFixtures.order(vehicle, RepairOrderStatus.SCHEDULED, Specialty.SUSPENSION);
        order.setMechanic(TestFixtures.mechanic("Vasil", Specialty.SUSPENSION));
        return order;
    }

    private ServiceTaskRequest request(String operation, String hours) {
        ServiceTaskRequest request = new ServiceTaskRequest();
        request.setOperation(operation);
        request.setHours(new BigDecimal(hours));
        return request;
    }
}
