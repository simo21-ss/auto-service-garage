package bg.softuni.garage.repairorder;

import bg.softuni.garage.TestFixtures;
import bg.softuni.garage.common.event.RepairOrderCancelledEvent;
import bg.softuni.garage.common.event.RepairOrderCompletedEvent;
import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import bg.softuni.garage.mechanic.Mechanic;
import bg.softuni.garage.mechanic.MechanicService;
import bg.softuni.garage.mechanic.Specialty;
import bg.softuni.garage.parts.PartsCatalogService;
import bg.softuni.garage.repairorder.dto.AssignmentRequest;
import bg.softuni.garage.repairorder.dto.RepairOrderRequest;
import bg.softuni.garage.user.RoleName;
import bg.softuni.garage.user.User;
import bg.softuni.garage.user.UserService;
import bg.softuni.garage.vehicle.Vehicle;
import bg.softuni.garage.vehicle.VehicleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairOrderServiceImplTest {

    @Mock
    private RepairOrderRepository repairOrderRepository;

    @Mock
    private ServiceTaskRepository serviceTaskRepository;

    @Mock
    private VehicleService vehicleService;

    @Mock
    private MechanicService mechanicService;

    @Mock
    private UserService userService;

    @Mock
    private PartsCatalogService partsCatalogService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RepairOrderServiceImpl repairOrderService;

    @Test
    void bookingCreatesARequestedOrderWithAGeneratedReference() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        when(vehicleService.getOwnedById(vehicle.getId(), owner.getId())).thenReturn(vehicle);
        when(repairOrderRepository.existsByVehicleAndStatusIn(any(Vehicle.class), anyCollection()))
                .thenReturn(false);
        when(repairOrderRepository.countByReferencePrefix(any())).thenReturn(4L);
        when(repairOrderRepository.save(any(RepairOrder.class))).thenAnswer(call -> call.getArgument(0));

        RepairOrder booked = repairOrderService.book(
                bookingRequest(vehicle.getId(), Specialty.SUSPENSION), owner.getId());

        assertThat(booked.getStatus()).isEqualTo(RepairOrderStatus.REQUESTED);
        assertThat(booked.getReference()).endsWith("-0005");
        assertThat(booked.getLabourCost()).isEqualByComparingTo("0.00");
        assertThat(booked.getPartsCost()).isEqualByComparingTo("0.00");
    }

    @Test
    void aRetiredVehicleCannotBeBookedIn() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        vehicle.setActive(false);
        when(vehicleService.getOwnedById(vehicle.getId(), owner.getId())).thenReturn(vehicle);

        assertThatThrownBy(() -> repairOrderService.book(
                bookingRequest(vehicle.getId(), Specialty.SUSPENSION), owner.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("retired");
    }

    @Test
    void aVehicleCannotHaveTwoOpenOrders() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        when(vehicleService.getOwnedById(vehicle.getId(), owner.getId())).thenReturn(vehicle);
        when(repairOrderRepository.existsByVehicleAndStatusIn(any(Vehicle.class), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> repairOrderService.book(
                bookingRequest(vehicle.getId(), Specialty.SUSPENSION), owner.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has an open");
    }

    @Test
    void assigningRequiresAMatchingSpecialty() {
        RepairOrder order = openOrder(RepairOrderStatus.REQUESTED, Specialty.SUSPENSION);
        Mechanic engineSpecialist = TestFixtures.mechanic("Georgi", Specialty.ENGINE);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(mechanicService.getById(engineSpecialist.getId())).thenReturn(engineSpecialist);

        assertThatThrownBy(() -> repairOrderService.assign(order.getId(),
                assignment(engineSpecialist.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("specialises in ENGINE");
    }

    @Test
    void assigningRequiresAnActiveMechanic() {
        RepairOrder order = openOrder(RepairOrderStatus.REQUESTED, Specialty.SUSPENSION);
        Mechanic mechanic = TestFixtures.mechanic("Vasil", Specialty.SUSPENSION);
        mechanic.setActive(false);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(mechanicService.getById(mechanic.getId())).thenReturn(mechanic);

        assertThatThrownBy(() -> repairOrderService.assign(order.getId(), assignment(mechanic.getId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not currently available");
    }

    @Test
    void assigningSchedulesTheOrder() {
        RepairOrder order = openOrder(RepairOrderStatus.REQUESTED, Specialty.SUSPENSION);
        Mechanic mechanic = TestFixtures.mechanic("Vasil", Specialty.SUSPENSION);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(mechanicService.getById(mechanic.getId())).thenReturn(mechanic);
        when(repairOrderRepository.save(any(RepairOrder.class))).thenAnswer(call -> call.getArgument(0));

        RepairOrder assigned = repairOrderService.assign(order.getId(), assignment(mechanic.getId()));

        assertThat(assigned.getStatus()).isEqualTo(RepairOrderStatus.SCHEDULED);
        assertThat(assigned.getMechanic()).isEqualTo(mechanic);
        assertThat(assigned.getScheduledAt()).isNotNull();
    }

    @Test
    void aCompletedOrderCannotBeReassigned() {
        RepairOrder order = openOrder(RepairOrderStatus.COMPLETED, Specialty.SUSPENSION);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> repairOrderService.assign(order.getId(), assignment(UUID.randomUUID())))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void completingRequiresTheOrderToBeInProgress() {
        RepairOrder order = openOrder(RepairOrderStatus.SCHEDULED, Specialty.SUSPENSION);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> repairOrderService.complete(order.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("in progress");
    }

    @Test
    void completingRequiresAtLeastOneTask() {
        RepairOrder order = openOrder(RepairOrderStatus.IN_PROGRESS, Specialty.SUSPENSION);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(serviceTaskRepository.findAllByRepairOrderOrderByCreatedAtAsc(order)).thenReturn(List.of());

        assertThatThrownBy(() -> repairOrderService.complete(order.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one service task");
    }

    @Test
    void completingIsBlockedWhileTasksArePending() {
        RepairOrder order = openOrder(RepairOrderStatus.IN_PROGRESS, Specialty.SUSPENSION);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(serviceTaskRepository.findAllByRepairOrderOrderByCreatedAtAsc(order))
                .thenReturn(List.of(TestFixtures.task(order, ServiceTaskStatus.PENDING, "2.0")));
        when(serviceTaskRepository.countByRepairOrderAndStatus(order, ServiceTaskStatus.PENDING))
                .thenReturn(1L);

        assertThatThrownBy(() -> repairOrderService.complete(order.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("still pending");
    }

    @Test
    void completingConsumesPartsAndPublishesAnEvent() {
        RepairOrder order = openOrder(RepairOrderStatus.IN_PROGRESS, Specialty.SUSPENSION);
        order.setMechanic(TestFixtures.mechanic("Vasil", Specialty.SUSPENSION));
        order.setLabourCost(new BigDecimal("110.00"));
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(serviceTaskRepository.findAllByRepairOrderOrderByCreatedAtAsc(order))
                .thenReturn(List.of(TestFixtures.task(order, ServiceTaskStatus.DONE, "2.0")));
        when(serviceTaskRepository.countByRepairOrderAndStatus(order, ServiceTaskStatus.PENDING))
                .thenReturn(0L);
        when(partsCatalogService.consumeAllFor(order.getId())).thenReturn(new BigDecimal("48.00"));
        when(repairOrderRepository.save(any(RepairOrder.class))).thenAnswer(call -> call.getArgument(0));

        RepairOrder completed = repairOrderService.complete(order.getId());

        assertThat(completed.getStatus()).isEqualTo(RepairOrderStatus.COMPLETED);
        assertThat(completed.getPartsCost()).isEqualByComparingTo("48.00");
        assertThat(completed.getCompletedAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(RepairOrderCompletedEvent.class));
    }

    @Test
    void cancellingReleasesPartsAndPublishesAnEvent() {
        RepairOrder order = openOrder(RepairOrderStatus.SCHEDULED, Specialty.SUSPENSION);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(partsCatalogService.releaseAllFor(order.getId())).thenReturn(2);
        when(repairOrderRepository.save(any(RepairOrder.class))).thenAnswer(call -> call.getArgument(0));

        RepairOrder cancelled = repairOrderService.cancel(order.getId(), UUID.randomUUID(), true);

        assertThat(cancelled.getStatus()).isEqualTo(RepairOrderStatus.CANCELLED);
        verify(partsCatalogService).releaseAllFor(order.getId());
        verify(eventPublisher).publishEvent(any(RepairOrderCancelledEvent.class));
    }

    @Test
    void aCompletedOrderCannotBeCancelled() {
        RepairOrder order = openOrder(RepairOrderStatus.COMPLETED, Specialty.SUSPENSION);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> repairOrderService.cancel(order.getId(), UUID.randomUUID(), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("completed repair order");
    }

    @Test
    void anAlreadyCancelledOrderCannotBeCancelledAgain() {
        RepairOrder order = openOrder(RepairOrderStatus.CANCELLED, Specialty.SUSPENSION);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> repairOrderService.cancel(order.getId(), UUID.randomUUID(), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already cancelled");
    }

    @Test
    void aCustomerCannotCancelWorkAlreadyUnderway() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        RepairOrder order = TestFixtures.order(vehicle, RepairOrderStatus.IN_PROGRESS, Specialty.SUSPENSION);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> repairOrderService.cancel(order.getId(), owner.getId(), false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("call the workshop");
    }

    @Test
    void aCustomerCannotOpenSomebodyElsesOrder() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        RepairOrder order = TestFixtures.order(vehicle, RepairOrderStatus.SCHEDULED, Specialty.SUSPENSION);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                repairOrderService.getForViewer(order.getId(), UUID.randomUUID(), false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void staffCanOpenAnyOrder() {
        RepairOrder order = openOrder(RepairOrderStatus.SCHEDULED, Specialty.SUSPENSION);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThat(repairOrderService.getForViewer(order.getId(), UUID.randomUUID(), true)).isEqualTo(order);
    }

    @Test
    void getByIdThrowsForAnUnknownOrder() {
        UUID id = UUID.randomUUID();
        when(repairOrderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repairOrderService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listAndCountQueriesDelegateToTheRepository() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(userService.getById(owner.getId())).thenReturn(owner);
        when(repairOrderRepository.findAllByVehicleOwnerOrderByCreatedAtDesc(owner)).thenReturn(List.of());
        when(repairOrderRepository.findAllByStatusInOrderByCreatedAtAsc(anyList())).thenReturn(List.of());
        when(repairOrderRepository.findAllByStatusOrderByCreatedAtDesc(RepairOrderStatus.COMPLETED))
                .thenReturn(List.of());
        when(repairOrderRepository.countByStatus(RepairOrderStatus.REQUESTED)).thenReturn(2L);
        when(repairOrderRepository.countByStatusIn(anyCollection())).thenReturn(5L);

        assertThat(repairOrderService.findForCustomer(owner.getId())).isEmpty();
        assertThat(repairOrderService.findOpenOrders()).isEmpty();
        assertThat(repairOrderService.findByStatus(RepairOrderStatus.COMPLETED)).isEmpty();
        assertThat(repairOrderService.countByStatus(RepairOrderStatus.REQUESTED)).isEqualTo(2L);
        assertThat(repairOrderService.countOpen()).isEqualTo(5L);
    }

    @Test
    void completingNeverPublishesWhenTheOrderIsRejected() {
        RepairOrder order = openOrder(RepairOrderStatus.SCHEDULED, Specialty.SUSPENSION);
        when(repairOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> repairOrderService.complete(order.getId()));

        verify(eventPublisher, never()).publishEvent(any(RepairOrderCompletedEvent.class));
    }

    private RepairOrder openOrder(RepairOrderStatus status, Specialty specialty) {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        return TestFixtures.order(vehicle, status, specialty);
    }

    private RepairOrderRequest bookingRequest(UUID vehicleId, Specialty specialty) {
        RepairOrderRequest request = new RepairOrderRequest();
        request.setVehicleId(vehicleId);
        request.setRequiredSpecialty(specialty);
        request.setComplaint("Knocking noise over bumps at the front axle");
        return request;
    }

    private AssignmentRequest assignment(UUID mechanicId) {
        AssignmentRequest request = new AssignmentRequest();
        request.setMechanicId(mechanicId);
        request.setScheduledAt(LocalDateTime.now().plusDays(2));
        return request;
    }
}
