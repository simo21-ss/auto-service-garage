package bg.softuni.garage.api;

import bg.softuni.garage.mechanic.MechanicService;
import bg.softuni.garage.mechanic.Specialty;
import bg.softuni.garage.parts.PartsClient;
import bg.softuni.garage.parts.dto.PartCollection;
import bg.softuni.garage.parts.dto.PartView;
import bg.softuni.garage.parts.dto.ReservationCollection;
import bg.softuni.garage.repairorder.RepairOrder;
import bg.softuni.garage.repairorder.RepairOrderService;
import bg.softuni.garage.repairorder.ServiceTaskService;
import bg.softuni.garage.repairorder.dto.AssignmentRequest;
import bg.softuni.garage.repairorder.dto.RepairOrderRequest;
import bg.softuni.garage.repairorder.dto.ServiceTaskRequest;
import bg.softuni.garage.user.GarageUserDetails;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalisationAndExportApiTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(7000);

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
    private ServiceTaskService serviceTaskService;

    @MockitoBean
    private PartsClient partsClient;

    @BeforeEach
    void stubThePartsService() {
        when(partsClient.catalogue()).thenReturn(new PartCollection(
                new PartCollection.Embedded(List.of(new PartView(UUID.randomUUID(), "BRK-1",
                        "Front brake disc", "BRAKES", new BigDecimal("78.50"),
                        20, 0, 20, 5, false, "Bosch Bulgaria")))));
        when(partsClient.reservationsFor(any(UUID.class))).thenReturn(
                new ReservationCollection(new ReservationCollection.Embedded(List.of())));
    }

    @Test
    void theHomePageRendersInEnglishByDefault() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your car deserves a proper workshop")));
    }

    @Test
    void theLanguageParameterSwitchesTheHomePageToBulgarian() throws Exception {
        mockMvc.perform(get("/").param("lang", "bg"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Колата ви заслужава истински сервиз")));
    }

    @Test
    void theLoginPageIsLocalisedToo() throws Exception {
        mockMvc.perform(get("/login").param("lang", "bg"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Потребителско име")));

        mockMvc.perform(get("/login"))
                .andExpect(content().string(containsString("Username")));
    }

    @Test
    void navigationLabelsAreLocalised() throws Exception {
        mockMvc.perform(get("/about").param("lang", "bg"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("За нас")))
                .andExpect(content().string(containsString("За сервиза")));
    }

    @Test
    void anUnknownLanguageFallsBackToEnglish() throws Exception {
        mockMvc.perform(get("/").param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your car deserves a proper workshop")));
    }

    @Test
    void aCompletedOrderCanBeDownloadedAsAPdfInvoice() throws Exception {
        User customer = registerCustomer();
        RepairOrder order = completedOrderFor(customer);

        byte[] pdf = mockMvc.perform(get("/orders/{id}/invoice", order.getId())
                        .with(user(new GarageUserDetails(customer))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("invoice-" + order.getReference() + ".pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void anInvoiceRendersEvenWithNoPartsFitted() throws Exception {
        User customer = registerCustomer();
        RepairOrder order = completedOrderFor(customer);

        byte[] pdf = mockMvc.perform(get("/orders/{id}/invoice", order.getId())
                        .with(user(new GarageUserDetails(customer))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdf.length).isGreaterThan(500);
    }

    @Test
    void aCustomerCannotDownloadSomebodyElsesInvoice() throws Exception {
        User owner = registerCustomer();
        User stranger = registerCustomer();
        RepairOrder order = completedOrderFor(owner);

        mockMvc.perform(get("/orders/{id}/invoice", order.getId())
                        .with(user(new GarageUserDetails(stranger))))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdministratorExportsTheInventoryAsAWorkbook() throws Exception {
        GarageUserDetails admin =
                new GarageUserDetails(userRepository.findByUsername("admin").orElseThrow());

        byte[] xlsx = mockMvc.perform(get("/admin/inventory/export").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inventory.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(xlsx).isNotEmpty();
        assertThat(xlsx[0]).isEqualTo((byte) 'P');
        assertThat(xlsx[1]).isEqualTo((byte) 'K');
    }

    @Test
    void aCustomerCannotExportTheInventory() throws Exception {
        User customer = registerCustomer();

        mockMvc.perform(get("/admin/inventory/export").with(user(new GarageUserDetails(customer))))
                .andExpect(status().isForbidden());
    }

    private RepairOrder completedOrderFor(User customer) {
        Vehicle vehicle = vehicleService.register(vehicleRequest(), customer.getId());
        RepairOrder order = repairOrderService.book(bookingRequest(vehicle.getId()), customer.getId());

        AssignmentRequest assignment = new AssignmentRequest();
        assignment.setMechanicId(mechanicService.findActive().stream()
                .filter(m -> m.getSpecialty() == Specialty.SUSPENSION).findFirst().orElseThrow().getId());
        assignment.setScheduledAt(LocalDateTime.now().plusDays(1));
        repairOrderService.assign(order.getId(), assignment);

        ServiceTaskRequest task = new ServiceTaskRequest();
        task.setOperation("Replace both front control arm bushings");
        task.setHours(new BigDecimal("2.5"));
        serviceTaskService.complete(order.getId(),
                serviceTaskService.add(order.getId(), task).getId());

        return repairOrderService.complete(order.getId());
    }

    private User registerCustomer() {
        int index = SEQUENCE.incrementAndGet();
        RegisterRequest request = new RegisterRequest();
        request.setUsername("export" + index);
        request.setEmail("export" + index + "@mail.bg");
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
        request.setPlate("CT " + (3000 + index) + " QQ");
        request.setVin("WVWZZZ1KZGW8000" + (index % 10));
        request.setMake("Skoda");
        request.setModel("Octavia");
        request.setModelYear(2017);
        request.setMileage(110000);
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
