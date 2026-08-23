package bg.softuni.garage.api;

import bg.softuni.garage.parts.PartsClient;
import bg.softuni.garage.parts.dto.PartCollection;
import bg.softuni.garage.parts.dto.PartView;
import bg.softuni.garage.parts.dto.ReservationCollection;
import bg.softuni.garage.user.User;
import bg.softuni.garage.user.UserService;
import bg.softuni.garage.user.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebEndpointsApiTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @MockitoBean
    private PartsClient partsClient;

    @BeforeEach
    void stubThePartsService() {
        when(partsClient.catalogue()).thenReturn(new PartCollection(
                new PartCollection.Embedded(List.of(new PartView(UUID.randomUUID(), "BRK-1",
                        "Front brake disc", "BRAKES", new BigDecimal("78.50"),
                        20, 0, 20, 5, false, "Bosch Bulgaria")))));
        when(partsClient.lowStock()).thenReturn(new PartCollection(
                new PartCollection.Embedded(List.of())));
        when(partsClient.reservationsFor(any(UUID.class))).thenReturn(
                new ReservationCollection(new ReservationCollection.Embedded(List.of())));
    }

    @Test
    void openPagesAreReachableWithoutSigningIn() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"));
        mockMvc.perform(get("/about")).andExpect(status().isOk()).andExpect(view().name("about"));
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/register")).andExpect(status().isOk());
    }

    @Test
    void theHomePageCarriesLiveWorkshopStatistics() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("openOrders", "completedOrders", "availableMechanics"));
    }

    @Test
    void protectedPagesRedirectAnonymousVisitorsToTheLoginPage() throws Exception {
        mockMvc.perform(get("/vehicles")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/orders")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/profile")).andExpect(status().is3xxRedirection());
    }

    @Test
    void aCustomerCannotOpenTheWorkshopBoard() throws Exception {
        mockMvc.perform(get("/workshop").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMechanicCanOpenTheWorkshopBoard() throws Exception {
        mockMvc.perform(get("/workshop").with(user("mechanic").roles("MECHANIC")))
                .andExpect(status().isOk())
                .andExpect(view().name("workshop/board"))
                .andExpect(model().attributeExists("openOrders", "requestedCount"));
    }

    @Test
    void aMechanicCannotManageUsers() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("mechanic").roles("MECHANIC")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorCanManageUsersMechanicsInventoryAndAudit() throws Exception {
        var admin = user("admin").roles("ADMIN")
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("USER_MANAGE"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("MECHANIC_MANAGE"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("PART_RESTOCK"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));

        mockMvc.perform(get("/admin/users").with(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/admin/mechanics").with(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/admin/inventory").with(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/admin/audit").with(admin)).andExpect(status().isOk());
    }

    @Test
    void thePartsCatalogueRendersForAnyAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/parts").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(view().name("parts/catalogue"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BRK-1")));
    }

    @Test
    void registrationRejectsInvalidInputAndRedisplaysTheForm() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "x")
                        .param("email", "not-an-email")
                        .param("password", "1")
                        .param("confirmPassword", "2")
                        .param("firstName", "")
                        .param("lastName", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("registerRequest",
                        "username", "email", "password", "firstName", "lastName"));
    }

    @Test
    void registrationSucceedsAndRedirectsToTheLoginPage() throws Exception {
        int index = SEQUENCE.incrementAndGet();

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "apiuser" + index)
                        .param("email", "apiuser" + index + "@mail.bg")
                        .param("password", "secret123")
                        .param("confirmPassword", "secret123")
                        .param("firstName", "Ivan")
                        .param("lastName", "Kolev")
                        .param("phone", "+359881234567"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void aPostWithoutACsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "nocsrf")
                        .param("email", "nocsrf@mail.bg")
                        .param("password", "secret123")
                        .param("confirmPassword", "secret123")
                        .param("firstName", "Ivan")
                        .param("lastName", "Kolev"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSignedInCustomerSeesTheirOwnProfileAndVehicles() throws Exception {
        User customer = registerCustomer();

        mockMvc.perform(get("/profile").with(user(principalOf(customer))))
                .andExpect(status().isOk())
                .andExpect(view().name("profile/view"))
                .andExpect(model().attributeExists("user"));

        mockMvc.perform(get("/vehicles").with(user(principalOf(customer))))
                .andExpect(status().isOk())
                .andExpect(view().name("vehicles/list"));
    }

    @Test
    void aCustomerCanRegisterAVehicleThroughTheForm() throws Exception {
        User customer = registerCustomer();
        int index = SEQUENCE.incrementAndGet();

        mockMvc.perform(post("/vehicles").with(user(principalOf(customer))).with(csrf())
                        .param("plate", "CB " + (5000 + index) + " ZZ")
                        .param("vin", "WVWZZZ1KZGW77777")
                        .param("make", "Toyota")
                        .param("model", "Corolla")
                        .param("modelYear", "2019")
                        .param("mileage", "60000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vehicles"));
    }

    @Test
    void anInvalidVehicleFormIsRedisplayedWithErrors() throws Exception {
        User customer = registerCustomer();

        mockMvc.perform(post("/vehicles").with(user(principalOf(customer))).with(csrf())
                        .param("plate", "")
                        .param("vin", "short")
                        .param("make", "")
                        .param("model", "")
                        .param("modelYear", "1800")
                        .param("mileage", "-5"))
                .andExpect(status().isOk())
                .andExpect(view().name("vehicles/form"))
                .andExpect(model().attributeHasFieldErrors("vehicleRequest", "plate", "vin", "make"));
    }

    @Test
    void anUnknownOrderRendersTheErrorViewRatherThanAWhitelabelPage() throws Exception {
        User customer = registerCustomer();

        mockMvc.perform(get("/orders/{id}", UUID.randomUUID()).with(user(principalOf(customer))))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("status", 404));
    }

    @Test
    void aMalformedIdentifierIsReportedAsABadRequest() throws Exception {
        User customer = registerCustomer();

        mockMvc.perform(get("/orders/not-a-uuid").with(user(principalOf(customer))))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error"));
    }

    @Test
    void aCustomerCanOpenTheirOwnOrdersList() throws Exception {
        User customer = registerCustomer();

        mockMvc.perform(get("/orders").with(user(principalOf(customer))))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/list"))
                .andExpect(model().attributeExists("orders"));
    }

    @Test
    void theBookingFormListsTheCustomersVehicles() throws Exception {
        User customer = registerCustomer();

        mockMvc.perform(get("/orders/new").with(user(principalOf(customer))))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/form"))
                .andExpect(model().attributeExists("vehicles", "specialties"));
    }

    @Test
    void theProfileEditFormIsPrefilled() throws Exception {
        User customer = registerCustomer();

        mockMvc.perform(get("/profile/edit").with(user(principalOf(customer))))
                .andExpect(status().isOk())
                .andExpect(view().name("profile/edit"))
                .andExpect(model().attributeExists("profileRequest"));
    }

    private User registerCustomer() {
        int index = SEQUENCE.incrementAndGet();
        RegisterRequest request = new RegisterRequest();
        request.setUsername("webuser" + index);
        request.setEmail("webuser" + index + "@mail.bg");
        request.setPassword("secret123");
        request.setConfirmPassword("secret123");
        request.setFirstName("Ivan");
        request.setLastName("Kolev");
        request.setPhone("+359881234567");
        return userService.register(request);
    }

    private bg.softuni.garage.user.GarageUserDetails principalOf(User user) {
        return new bg.softuni.garage.user.GarageUserDetails(user);
    }
}
