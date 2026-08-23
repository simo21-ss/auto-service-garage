package bg.softuni.garage.api;

import bg.softuni.garage.mechanic.Mechanic;
import bg.softuni.garage.mechanic.MechanicService;
import bg.softuni.garage.mechanic.Specialty;
import bg.softuni.garage.parts.PartsClient;
import bg.softuni.garage.parts.dto.PartCollection;
import bg.softuni.garage.parts.dto.PartView;
import bg.softuni.garage.parts.dto.ReservationCollection;
import bg.softuni.garage.parts.dto.ReservationCommand;
import bg.softuni.garage.parts.dto.ReservationView;
import bg.softuni.garage.parts.dto.RestockCommand;
import bg.softuni.garage.repairorder.RepairOrder;
import bg.softuni.garage.repairorder.RepairOrderService;
import bg.softuni.garage.repairorder.dto.RepairOrderRequest;
import bg.softuni.garage.user.GarageUserDetails;
import bg.softuni.garage.user.RoleName;
import bg.softuni.garage.user.User;
import bg.softuni.garage.user.UserRepository;
import bg.softuni.garage.user.UserService;
import bg.softuni.garage.user.dto.RegisterRequest;
import bg.softuni.garage.vehicle.Vehicle;
import bg.softuni.garage.vehicle.VehicleService;
import bg.softuni.garage.vehicle.dto.VehicleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowActionsApiTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(1000);
    private static final DateTimeFormatter SLOT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private MechanicService mechanicService;

    @Autowired
    private RepairOrderService repairOrderService;

    @Autowired
    private bg.softuni.garage.repairorder.ServiceTaskService serviceTaskService;

    @MockitoBean
    private PartsClient partsClient;

    private GarageUserDetails staff;

    @BeforeEach
    void stubPartsAndResolveStaff() {
        PartView part = new PartView(UUID.randomUUID(), "BRK-1", "Front brake disc", "BRAKES",
                new BigDecimal("78.50"), 20, 0, 20, 5, false, "Bosch Bulgaria");

        when(partsClient.catalogue())
                .thenReturn(new PartCollection(new PartCollection.Embedded(List.of(part))));
        when(partsClient.lowStock())
                .thenReturn(new PartCollection(new PartCollection.Embedded(List.of())));
        when(partsClient.reservationsFor(any(UUID.class)))
                .thenReturn(new ReservationCollection(new ReservationCollection.Embedded(List.of())));
        when(partsClient.reserve(any(ReservationCommand.class))).thenReturn(reservationView());
        when(partsClient.release(any(UUID.class))).thenReturn(reservationView());
        when(partsClient.restock(any(UUID.class), any(RestockCommand.class))).thenReturn(part);

        staff = new GarageUserDetails(userRepository.findByUsername("admin").orElseThrow());
    }

    @Test
    void aMechanicIsCreatedEditedAndRemovedThroughTheAdminPages() throws Exception {
        int index = SEQUENCE.incrementAndGet();
        String name = "Temp Mechanic " + index;

        mockMvc.perform(get("/admin/mechanics/new").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/mechanic-form"));

        mockMvc.perform(post("/admin/mechanics").with(user(staff)).with(csrf())
                        .param("fullName", name)
                        .param("specialty", "BODYWORK")
                        .param("hourlyRate", "45.00")
                        .param("hiredOn", "2024-01-15")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/mechanics"))
                .andExpect(flash().attributeExists("success"));

        Mechanic created = mechanicService.findAll().stream()
                .filter(m -> m.getFullName().equals(name)).findFirst().orElseThrow();

        mockMvc.perform(get("/admin/mechanics/{id}/edit", created.getId()).with(user(staff)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/admin/mechanics/{id}", created.getId()).with(user(staff)).with(csrf())
                        .param("fullName", name)
                        .param("specialty", "BODYWORK")
                        .param("hourlyRate", "52.00")
                        .param("hiredOn", "2024-01-15")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(delete("/admin/mechanics/{id}", created.getId()).with(user(staff)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/mechanics"));
    }

    @Test
    void anInvalidMechanicFormIsRedisplayed() throws Exception {
        mockMvc.perform(post("/admin/mechanics").with(user(staff)).with(csrf())
                        .param("fullName", "")
                        .param("hourlyRate", "5.00")
                        .param("hiredOn", "2090-01-15"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/mechanic-form"));
    }

    @Test
    void aVehicleIsEditedRetiredAndDeleted() throws Exception {
        User customer = registerCustomer();
        GarageUserDetails principal = new GarageUserDetails(customer);
        Vehicle vehicle = vehicleService.register(vehicleRequest(), customer.getId());

        mockMvc.perform(get("/vehicles/{id}/edit", vehicle.getId()).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("vehicles/form"));

        mockMvc.perform(put("/vehicles/{id}", vehicle.getId()).with(user(principal)).with(csrf())
                        .param("plate", vehicle.getPlate())
                        .param("vin", vehicle.getVin())
                        .param("make", "Volkswagen")
                        .param("model", "Golf")
                        .param("modelYear", "2016")
                        .param("mileage", "150000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vehicles"));

        mockMvc.perform(put("/vehicles/{id}/status", vehicle.getId()).with(user(principal)).with(csrf())
                        .param("active", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        mockMvc.perform(delete("/vehicles/{id}", vehicle.getId()).with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vehicles"));
    }

    @Test
    void theWholeOrderWorkflowIsDrivenThroughTheWebLayer() throws Exception {
        User customer = registerCustomer();
        GarageUserDetails customerPrincipal = new GarageUserDetails(customer);
        Vehicle vehicle = vehicleService.register(vehicleRequest(), customer.getId());
        Mechanic mechanic = mechanicService.findActive().stream()
                .filter(m -> m.getSpecialty() == Specialty.SUSPENSION).findFirst().orElseThrow();

        mockMvc.perform(post("/orders").with(user(customerPrincipal)).with(csrf())
                        .param("vehicleId", vehicle.getId().toString())
                        .param("requiredSpecialty", "SUSPENSION")
                        .param("complaint", "Knocking noise over bumps at the front axle"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"));

        RepairOrder order = repairOrderService.findForCustomer(customer.getId()).getFirst();

        mockMvc.perform(get("/orders/{id}", order.getId()).with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/details"));

        mockMvc.perform(put("/orders/{id}/assign", order.getId()).with(user(staff)).with(csrf())
                        .param("mechanicId", mechanic.getId().toString())
                        .param("scheduledAt", LocalDateTime.now().plusDays(2).format(SLOT)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        mockMvc.perform(post("/orders/{id}/parts", order.getId()).with(user(staff)).with(csrf())
                        .param("sku", "BRK-1")
                        .param("quantity", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        mockMvc.perform(delete("/orders/{id}/parts/{reservationId}", order.getId(), UUID.randomUUID())
                        .with(user(staff)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/orders/{id}/tasks", order.getId()).with(user(staff)).with(csrf())
                        .param("operation", "Replace both front control arm bushings")
                        .param("hours", "2.5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        UUID taskId = serviceTaskService.findForOrder(repairOrderService.getById(order.getId()))
                .getFirst().getId();

        mockMvc.perform(put("/orders/{id}/complete", order.getId()).with(user(staff)).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/orders/{id}/tasks/{taskId}/complete", order.getId(), taskId)
                        .with(user(staff)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        mockMvc.perform(put("/orders/{id}/complete", order.getId()).with(user(staff)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        assertThat(repairOrderService.getById(order.getId()).getStatus())
                .isEqualTo(bg.softuni.garage.repairorder.RepairOrderStatus.COMPLETED);
    }

    @Test
    void anInvalidAssignmentIsReportedAsAFlashError() throws Exception {
        User customer = registerCustomer();
        Vehicle vehicle = vehicleService.register(vehicleRequest(), customer.getId());
        RepairOrder order = repairOrderService.book(bookingRequest(vehicle.getId()), customer.getId());

        mockMvc.perform(put("/orders/{id}/assign", order.getId()).with(user(staff)).with(csrf())
                        .param("scheduledAt", LocalDateTime.now().plusDays(2).format(SLOT)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void anInvalidTaskIsReportedAsAFlashError() throws Exception {
        User customer = registerCustomer();
        Vehicle vehicle = vehicleService.register(vehicleRequest(), customer.getId());
        RepairOrder order = repairOrderService.book(bookingRequest(vehicle.getId()), customer.getId());

        mockMvc.perform(post("/orders/{id}/tasks", order.getId()).with(user(staff)).with(csrf())
                        .param("operation", "x")
                        .param("hours", "0.01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void aCustomerCancelsTheirOwnOrder() throws Exception {
        User customer = registerCustomer();
        GarageUserDetails principal = new GarageUserDetails(customer);
        Vehicle vehicle = vehicleService.register(vehicleRequest(), customer.getId());
        RepairOrder order = repairOrderService.book(bookingRequest(vehicle.getId()), customer.getId());

        mockMvc.perform(put("/orders/{id}/cancel", order.getId()).with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"));
    }

    @Test
    void anAdministratorChangesARoleAndSuspendsAnAccount() throws Exception {
        User target = registerCustomer();

        mockMvc.perform(put("/admin/users/{id}/role", target.getId()).with(user(staff)).with(csrf())
                        .param("role", "MECHANIC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        mockMvc.perform(put("/admin/users/{id}/status", target.getId()).with(user(staff)).with(csrf())
                        .param("active", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        assertThat(userService.getById(target.getId()).getRole().getName()).isEqualTo(RoleName.MECHANIC);
    }

    @Test
    void anAdministratorRestocksAPart() throws Exception {
        mockMvc.perform(post("/admin/inventory/{id}/restock", UUID.randomUUID())
                        .with(user(staff)).with(csrf())
                        .param("quantity", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/inventory"));
    }

    @Test
    void aProfileIsUpdatedThroughTheWebLayer() throws Exception {
        User customer = registerCustomer();
        GarageUserDetails principal = new GarageUserDetails(customer);

        mockMvc.perform(put("/profile").with(user(principal)).with(csrf())
                        .param("email", "updated" + SEQUENCE.incrementAndGet() + "@mail.bg")
                        .param("firstName", "Ivan")
                        .param("lastName", "Kolev")
                        .param("phone", "+359887654321"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));
    }

    @Test
    void anInvalidProfileFormIsRedisplayed() throws Exception {
        User customer = registerCustomer();
        GarageUserDetails principal = new GarageUserDetails(customer);

        mockMvc.perform(put("/profile").with(user(principal)).with(csrf())
                        .param("email", "not-an-email")
                        .param("firstName", "")
                        .param("lastName", "Kolev"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile/edit"));
    }

    private ReservationView reservationView() {
        return new ReservationView(UUID.randomUUID(), UUID.randomUUID(), "BRK-1", "Front brake disc",
                2, new BigDecimal("78.50"), new BigDecimal("157.00"), "RESERVED",
                LocalDateTime.now(), null);
    }

    private User registerCustomer() {
        int index = SEQUENCE.incrementAndGet();
        RegisterRequest request = new RegisterRequest();
        request.setUsername("flow" + index);
        request.setEmail("flow" + index + "@mail.bg");
        request.setPassword("secret123");
        request.setConfirmPassword("secret123");
        request.setFirstName("Ivan");
        request.setLastName("Kolev");
        request.setPhone("+359881234567");
        return userService.register(request);
    }

    private VehicleRequest vehicleRequest() {
        int index = SEQUENCE.incrementAndGet();
        VehicleRequest request = new VehicleRequest();
        request.setPlate("CA " + (2000 + index) + " XY");
        request.setVin("WVWZZZ1KZGW9000" + (index % 10));
        request.setMake("Volkswagen");
        request.setModel("Passat");
        request.setModelYear(2018);
        request.setMileage(95000);
        return request;
    }

    private RepairOrderRequest bookingRequest(UUID vehicleId) {
        RepairOrderRequest request = new RepairOrderRequest();
        request.setVehicleId(vehicleId);
        request.setRequiredSpecialty(Specialty.SUSPENSION);
        request.setComplaint("Knocking noise over bumps at the front axle");
        return request;
    }
}
