package bg.softuni.partssvc;

import bg.softuni.partssvc.common.exception.InsufficientStockException;
import bg.softuni.partssvc.ledger.StockLedgerRepository;
import bg.softuni.partssvc.part.Part;
import bg.softuni.partssvc.part.PartCategory;
import bg.softuni.partssvc.part.PartRepository;
import bg.softuni.partssvc.part.PartService;
import bg.softuni.partssvc.part.dto.PartUpsertRequest;
import bg.softuni.partssvc.part.dto.RestockRequest;
import bg.softuni.partssvc.reservation.PartReservationRepository;
import bg.softuni.partssvc.reservation.ReservationService;
import bg.softuni.partssvc.reservation.ReservationStatus;
import bg.softuni.partssvc.reservation.dto.ReservationRequest;
import bg.softuni.partssvc.reservation.dto.ReservationResponse;
import bg.softuni.partssvc.supplier.Supplier;
import bg.softuni.partssvc.supplier.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ReservationFlowIntegrationTest {

    @Autowired
    private ReservationService reservationService;

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

    private UUID partId;

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

        partId = UUID.fromString(partService.create(new PartUpsertRequest("BRK-DISC-320",
                "Front brake disc", PartCategory.BRAKES, new BigDecimal("78.50"),
                20, 5, 20, "Bosch Bulgaria"), "system").id().toString());
    }

    @Test
    void reservingThenConsumingMovesStockThroughTheWholeLifecycle() {
        UUID repairOrderId = UUID.randomUUID();

        ReservationResponse reserved = reservationService.reserve(
                new ReservationRequest(repairOrderId, "BRK-DISC-320", 4), "mechanic");

        Part afterReserve = partRepository.findById(partId).orElseThrow();
        assertThat(afterReserve.getQuantityOnHand()).isEqualTo(20);
        assertThat(afterReserve.getQuantityReserved()).isEqualTo(4);
        assertThat(afterReserve.availableQuantity()).isEqualTo(16);

        reservationService.consume(reserved.id(), "mechanic");

        Part afterConsume = partRepository.findById(partId).orElseThrow();
        assertThat(afterConsume.getQuantityOnHand()).isEqualTo(16);
        assertThat(afterConsume.getQuantityReserved()).isZero();

        List<ReservationResponse> forOrder = reservationService.findForRepairOrder(repairOrderId);
        assertThat(forOrder).singleElement()
                .satisfies(r -> assertThat(r.status()).isEqualTo(ReservationStatus.CONSUMED));
    }

    @Test
    void releasingPutsTheStockBack() {
        ReservationResponse reserved = reservationService.reserve(
                new ReservationRequest(UUID.randomUUID(), "BRK-DISC-320", 6), "mechanic");

        reservationService.release(reserved.id(), "mechanic");

        Part part = partRepository.findById(partId).orElseThrow();
        assertThat(part.getQuantityOnHand()).isEqualTo(20);
        assertThat(part.getQuantityReserved()).isZero();
    }

    @Test
    void twoReservationsCannotOversellTheSameStock() {
        reservationService.reserve(new ReservationRequest(UUID.randomUUID(), "BRK-DISC-320", 18), "mechanic");

        assertThatThrownBy(() -> reservationService.reserve(
                new ReservationRequest(UUID.randomUUID(), "BRK-DISC-320", 5), "mechanic"))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void restockingRaisesAvailabilityAndClearsTheLowStockReport() {
        reservationService.reserve(new ReservationRequest(UUID.randomUUID(), "BRK-DISC-320", 16), "mechanic");
        assertThat(partService.findBelowReorderLevel()).isNotEmpty();

        partService.restock(partId, new RestockRequest(30, "delivery"), "admin");

        assertThat(partService.findBelowReorderLevel()).isEmpty();
        assertThat(partService.getById(partId).quantityAvailable()).isEqualTo(34);
    }
}
