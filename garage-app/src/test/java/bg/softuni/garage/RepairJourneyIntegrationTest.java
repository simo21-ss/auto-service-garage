package bg.softuni.garage;

import bg.softuni.garage.common.audit.AuditService;
import bg.softuni.garage.mechanic.Mechanic;
import bg.softuni.garage.mechanic.MechanicService;
import bg.softuni.garage.mechanic.Specialty;
import bg.softuni.garage.mechanic.dto.MechanicRequest;
import bg.softuni.garage.parts.PartsClient;
import bg.softuni.garage.parts.dto.ReservationCollection;
import bg.softuni.garage.repairorder.RepairOrder;
import bg.softuni.garage.repairorder.RepairOrderService;
import bg.softuni.garage.repairorder.RepairOrderStatus;
import bg.softuni.garage.repairorder.ServiceTask;
import bg.softuni.garage.repairorder.ServiceTaskService;
import bg.softuni.garage.repairorder.dto.AssignmentRequest;
import bg.softuni.garage.repairorder.dto.RepairOrderRequest;
import bg.softuni.garage.repairorder.dto.ServiceTaskRequest;
import bg.softuni.garage.user.RoleName;
import bg.softuni.garage.user.User;
import bg.softuni.garage.user.UserService;
import bg.softuni.garage.user.dto.RegisterRequest;
import bg.softuni.garage.vehicle.Vehicle;
import bg.softuni.garage.vehicle.VehicleService;
import bg.softuni.garage.vehicle.dto.VehicleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RepairJourneyIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private UserService userService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private MechanicService mechanicService;

    @Autowired
    private RepairOrderService repairOrderService;

    @Autowired
    private ServiceTaskService serviceTaskService;

    @Autowired
    private AuditService auditService;

    @MockitoBean
    private PartsClient partsClient;

    @Test
    void aCustomerCanBeRegisteredWithAHashedPasswordAndTheCustomerRole() {
        User registered = userService.register(registerRequest());

        assertThat(registered.getRole().getName()).isEqualTo(RoleName.CUSTOMER);
        assertThat(registered.getPassword()).startsWith("$2a$");
        assertThat(registered.getRole().getPermissions()).isNotEmpty();
    }

    @Test
    void theWholeRepairJourneyRunsThroughRealRepositories() {
        when(partsClient.reservationsFor(any(UUID.class)))
                .thenReturn(new ReservationCollection(new ReservationCollection.Embedded(List.of())));

        User customer = userService.register(registerRequest());
        Vehicle vehicle = vehicleService.register(vehicleRequest(), customer.getId());
        Mechanic mechanic = mechanicService.create(mechanicRequest());

        RepairOrder booked = repairOrderService.book(bookingRequest(vehicle.getId()), customer.getId());
        assertThat(booked.getStatus()).isEqualTo(RepairOrderStatus.REQUESTED);
        assertThat(booked.getReference()).startsWith("RO-");

        RepairOrder scheduled = repairOrderService.assign(booked.getId(), assignment(mechanic.getId()));
        assertThat(scheduled.getStatus()).isEqualTo(RepairOrderStatus.SCHEDULED);

        ServiceTask task = serviceTaskService.add(booked.getId(), taskRequest());
        RepairOrder inProgress = repairOrderService.getById(booked.getId());
        assertThat(inProgress.getStatus()).isEqualTo(RepairOrderStatus.IN_PROGRESS);
        assertThat(inProgress.getLabourCost()).isEqualByComparingTo("137.50");

        serviceTaskService.complete(booked.getId(), task.getId());
        RepairOrder completed = repairOrderService.complete(booked.getId());

        assertThat(completed.getStatus()).isEqualTo(RepairOrderStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(serviceTaskService.findForOrder(completed)).hasSize(1);
    }

    @Test
    void cancellingAnOrderRecordsAnAuditEntry() {
        when(partsClient.reservationsFor(any(UUID.class)))
                .thenReturn(new ReservationCollection(new ReservationCollection.Embedded(List.of())));

        User customer = userService.register(registerRequest());
        Vehicle vehicle = vehicleService.register(vehicleRequest(), customer.getId());
        RepairOrder booked = repairOrderService.book(bookingRequest(vehicle.getId()), customer.getId());

        long before = auditService.findRecent(100).size();
        repairOrderService.cancel(booked.getId(), customer.getId(), false);

        assertThat(auditService.findRecent(100)).hasSizeGreaterThan((int) before);
    }

    @Test
    void referencesAreUniquePerBooking() {
        User customer = userService.register(registerRequest());
        Vehicle first = vehicleService.register(vehicleRequest(), customer.getId());
        Vehicle second = vehicleService.register(vehicleRequest(), customer.getId());

        RepairOrder one = repairOrderService.book(bookingRequest(first.getId()), customer.getId());
        RepairOrder two = repairOrderService.book(bookingRequest(second.getId()), customer.getId());

        assertThat(one.getReference()).isNotEqualTo(two.getReference());
    }

    private RegisterRequest registerRequest() {
        int index = SEQUENCE.incrementAndGet();
        RegisterRequest request = new RegisterRequest();
        request.setUsername("customer" + index);
        request.setEmail("customer" + index + "@mail.bg");
        request.setPassword("secret123");
        request.setConfirmPassword("secret123");
        request.setFirstName("Ivan");
        request.setLastName("Kolev");
        request.setPhone("+359881234567");
        return request;
    }

    private VehicleRequest vehicleRequest() {
        int index = SEQUENCE.incrementAndGet();
        VehicleRequest request = new VehicleRequest();
        request.setPlate("CB " + (1000 + index) + " AB");
        request.setVin("WVWZZZ1KZGW12345" + (index % 10));
        request.setMake("Volkswagen");
        request.setModel("Golf");
        request.setModelYear(2016);
        request.setMileage(120000);
        return request;
    }

    private MechanicRequest mechanicRequest() {
        int index = SEQUENCE.incrementAndGet();
        MechanicRequest request = new MechanicRequest();
        request.setFullName("Vasil Marinov " + index);
        request.setSpecialty(Specialty.SUSPENSION);
        request.setHourlyRate(new BigDecimal("55.00"));
        request.setHiredOn(LocalDate.of(2020, 6, 8));
        request.setActive(true);
        return request;
    }

    private RepairOrderRequest bookingRequest(UUID vehicleId) {
        RepairOrderRequest request = new RepairOrderRequest();
        request.setVehicleId(vehicleId);
        request.setRequiredSpecialty(Specialty.SUSPENSION);
        request.setComplaint("Knocking noise over bumps at the front axle");
        return request;
    }

    private AssignmentRequest assignment(UUID mechanicId) {
        AssignmentRequest request = new AssignmentRequest();
        request.setMechanicId(mechanicId);
        request.setScheduledAt(LocalDateTime.now().plusDays(2));
        return request;
    }

    private ServiceTaskRequest taskRequest() {
        ServiceTaskRequest request = new ServiceTaskRequest();
        request.setOperation("Replace both front control arm bushings");
        request.setHours(new BigDecimal("2.5"));
        return request;
    }
}
