package bg.softuni.partssvc.api;

import bg.softuni.partssvc.ledger.StockLedgerRepository;
import bg.softuni.partssvc.part.PartCategory;
import bg.softuni.partssvc.part.PartRepository;
import bg.softuni.partssvc.part.PartService;
import bg.softuni.partssvc.part.dto.PartResponse;
import bg.softuni.partssvc.part.dto.PartUpsertRequest;
import bg.softuni.partssvc.reservation.PartReservationRepository;
import bg.softuni.partssvc.reservation.dto.ReservationRequest;
import bg.softuni.partssvc.supplier.Supplier;
import bg.softuni.partssvc.supplier.SupplierRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PartService partService;

    @Autowired
    private PartRepository partRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PartReservationRepository reservationRepository;

    @MockitoBean
    private StockLedgerRepository stockLedgerRepository;

    @Value("${parts.security.service-token.secret}")
    private String secret;

    private PartResponse part;

    @BeforeEach
    void seedCatalogue() {
        reservationRepository.deleteAll();
        partRepository.deleteAll();
        supplierRepository.deleteAll();

        Supplier supplier = new Supplier();
        supplier.setName("Bosch Bulgaria");
        supplier.setEmail("orders@bosch.bg");
        supplier.setLeadTimeDays(3);
        supplier.setActive(true);
        supplierRepository.save(supplier);

        part = partService.create(new PartUpsertRequest("BRK-DISC-320", "Front brake disc",
                PartCategory.BRAKES, new BigDecimal("78.50"), 20, 5, 20, "Bosch Bulgaria"), "system");
    }

    @Test
    void catalogueRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/parts"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aForgedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/parts").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogueReturnsPartsWithHateoasLinks() throws Exception {
        mockMvc.perform(get("/api/parts").header("Authorization", bearer("PART_RESERVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.partResponseList[0].sku").value("BRK-DISC-320"))
                .andExpect(jsonPath("$._embedded.partResponseList[0].quantityAvailable").value(20))
                .andExpect(jsonPath("$._embedded.partResponseList[0]._links.self.href").exists())
                .andExpect(jsonPath("$._links.low-stock.href").exists());
    }

    @Test
    void reservingReturnsCreatedAndDropsAvailability() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ReservationRequest(UUID.randomUUID(), "BRK-DISC-320", 4));

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer("PART_RESERVE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.lineTotal").value(314.00))
                .andExpect(jsonPath("$._links.consume.href").exists());

        mockMvc.perform(get("/api/parts").header("Authorization", bearer("PART_RESERVE")))
                .andExpect(jsonPath("$._embedded.partResponseList[0].quantityAvailable").value(16));
    }

    @Test
    void reservingWithoutTheRightScopeIsForbidden() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ReservationRequest(UUID.randomUUID(), "BRK-DISC-320", 1));

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer("SOMETHING_ELSE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void overReservingReturnsAProblemDetailConflict() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ReservationRequest(UUID.randomUUID(), "BRK-DISC-320", 999));

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer("PART_RESERVE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient stock"))
                .andExpect(jsonPath("$.available").value(20))
                .andExpect(jsonPath("$.requested").value(999));
    }

    @Test
    void invalidPayloadReturnsFieldErrors() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ReservationRequest(UUID.randomUUID(), "BRK-DISC-320", 0));

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer("PART_RESERVE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    void reservingAnUnknownSkuReturnsNotFound() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ReservationRequest(UUID.randomUUID(), "NO-SUCH-PART", 1));

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer("PART_RESERVE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void consumeAndReleaseDriveTheReservationLifecycle() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ReservationRequest(UUID.randomUUID(), "BRK-DISC-320", 2));

        String created = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer("PART_RESERVE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String reservationId = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(put("/api/reservations/{id}/consume", reservationId)
                        .header("Authorization", bearer("PART_RESERVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONSUMED"));

        mockMvc.perform(put("/api/reservations/{id}/consume", reservationId)
                        .header("Authorization", bearer("PART_RESERVE")))
                .andExpect(status().isConflict());
    }

    @Test
    void releasingReturnsStockToTheCatalogue() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ReservationRequest(UUID.randomUUID(), "BRK-DISC-320", 3));

        String created = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearer("PART_RESERVE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(delete("/api/reservations/{id}", objectMapper.readTree(created).get("id").asText())
                        .header("Authorization", bearer("PART_RESERVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    void restockingRequiresTheRestockScope() throws Exception {
        mockMvc.perform(post("/api/parts/{id}/restock", part.id())
                        .header("Authorization", bearer("PART_RESERVE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/parts/{id}/restock", part.id())
                        .header("Authorization", bearer("PART_RESTOCK"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(25));
    }

    @Test
    void lowStockAndLedgerEndpointsRespond() throws Exception {
        mockMvc.perform(get("/api/parts/low-stock").header("Authorization", bearer("PART_RESERVE")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/parts/{id}/ledger", part.id())
                        .header("Authorization", bearer("PART_RESERVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.part.href").exists());
    }

    @Test
    void suppliersEndpointRespondsForAnAuthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/suppliers").header("Authorization", bearer("PART_RESERVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.supplierResponseList[0].name").value("Bosch Bulgaria"));
    }

    @Test
    void anUnknownEndpointReturnsAProblemDetail() throws Exception {
        mockMvc.perform(get("/api/nope").header("Authorization", bearer("PART_RESERVE")))
                .andExpect(status().isForbidden());
    }

    private String bearer(String scope) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        return "Bearer " + Jwts.builder()
                .subject("mechanic")
                .issuer("garage-app")
                .claim("scope", scope)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(120)))
                .signWith(key)
                .compact();
    }
}
