package bg.softuni.partssvc.reservation;

import bg.softuni.partssvc.TestFixtures;
import bg.softuni.partssvc.common.event.StockDepletedEvent;
import bg.softuni.partssvc.common.exception.InsufficientStockException;
import bg.softuni.partssvc.common.exception.ReservationNotFoundException;
import bg.softuni.partssvc.common.exception.ReservationStateException;
import bg.softuni.partssvc.ledger.StockLedgerService;
import bg.softuni.partssvc.part.Part;
import bg.softuni.partssvc.part.PartRepository;
import bg.softuni.partssvc.part.PartService;
import bg.softuni.partssvc.reservation.dto.ReservationRequest;
import bg.softuni.partssvc.reservation.dto.ReservationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    @Mock
    private PartReservationRepository reservationRepository;

    @Mock
    private PartRepository partRepository;

    @Mock
    private PartService partService;

    @Mock
    private StockLedgerService stockLedgerService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    @Test
    void reserveMovesStockFromAvailableToReserved() {
        Part part = TestFixtures.part("BRK-1", 20, 0, 2);
        when(partService.getEntityBySku("BRK-1")).thenReturn(part);
        when(reservationRepository.save(any(PartReservation.class)))
                .thenAnswer(call -> call.getArgument(0));

        ReservationResponse response = reservationService.reserve(
                new ReservationRequest(UUID.randomUUID(), "BRK-1", 5), "mechanic");

        assertThat(part.getQuantityReserved()).isEqualTo(5);
        assertThat(part.getQuantityOnHand()).isEqualTo(20);
        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(response.lineTotal()).isEqualByComparingTo("250.00");
        verify(partRepository).save(part);
    }

    @Test
    void reserveRejectsMoreThanIsAvailable() {
        Part part = TestFixtures.part("BRK-1", 10, 8, 2);
        when(partService.getEntityBySku("BRK-1")).thenReturn(part);

        assertThatThrownBy(() -> reservationService.reserve(
                new ReservationRequest(UUID.randomUUID(), "BRK-1", 5), "mechanic"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Only 2 unit(s)");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserveCountsReservedStockAsUnavailable() {
        Part part = TestFixtures.part("BRK-1", 10, 10, 2);
        when(partService.getEntityBySku("BRK-1")).thenReturn(part);

        assertThatThrownBy(() -> reservationService.reserve(
                new ReservationRequest(UUID.randomUUID(), "BRK-1", 1), "mechanic"))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void reservePublishesAnEventWhenStockDropsToTheReorderLevel() {
        Part part = TestFixtures.part("BRK-1", 10, 0, 5);
        when(partService.getEntityBySku("BRK-1")).thenReturn(part);
        when(reservationRepository.save(any(PartReservation.class)))
                .thenAnswer(call -> call.getArgument(0));

        reservationService.reserve(new ReservationRequest(UUID.randomUUID(), "BRK-1", 5), "mechanic");

        verify(eventPublisher).publishEvent(any(StockDepletedEvent.class));
    }

    @Test
    void reserveDoesNotPublishWhenStockStaysHealthy() {
        Part part = TestFixtures.part("BRK-1", 30, 0, 5);
        when(partService.getEntityBySku("BRK-1")).thenReturn(part);
        when(reservationRepository.save(any(PartReservation.class)))
                .thenAnswer(call -> call.getArgument(0));

        reservationService.reserve(new ReservationRequest(UUID.randomUUID(), "BRK-1", 1), "mechanic");

        verify(eventPublisher, never()).publishEvent(any(StockDepletedEvent.class));
    }

    @Test
    void consumeRemovesStockFromOnHandAndClearsTheReservation() {
        Part part = TestFixtures.part("BRK-1", 20, 5, 2);
        PartReservation reservation = TestFixtures.reservation(part, 5, ReservationStatus.RESERVED);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(PartReservation.class)))
                .thenAnswer(call -> call.getArgument(0));

        ReservationResponse response = reservationService.consume(reservation.getId(), "mechanic");

        assertThat(part.getQuantityOnHand()).isEqualTo(15);
        assertThat(part.getQuantityReserved()).isZero();
        assertThat(response.status()).isEqualTo(ReservationStatus.CONSUMED);
        assertThat(response.resolvedAt()).isNotNull();
        verify(stockLedgerService).record(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void consumeRejectsAReservationThatIsNoLongerOpen() {
        Part part = TestFixtures.part("BRK-1", 20, 0, 2);
        PartReservation reservation = TestFixtures.reservation(part, 5, ReservationStatus.CONSUMED);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.consume(reservation.getId(), "mechanic"))
                .isInstanceOf(ReservationStateException.class)
                .hasMessageContaining("CONSUMED");
    }

    @Test
    void releaseReturnsStockWithoutTouchingOnHand() {
        Part part = TestFixtures.part("BRK-1", 20, 5, 2);
        PartReservation reservation = TestFixtures.reservation(part, 5, ReservationStatus.RESERVED);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(PartReservation.class)))
                .thenAnswer(call -> call.getArgument(0));

        ReservationResponse response = reservationService.release(reservation.getId(), "mechanic");

        assertThat(part.getQuantityOnHand()).isEqualTo(20);
        assertThat(part.getQuantityReserved()).isZero();
        assertThat(response.status()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void releaseRejectsAnAlreadyReleasedReservation() {
        Part part = TestFixtures.part("BRK-1", 20, 0, 2);
        PartReservation reservation = TestFixtures.reservation(part, 5, ReservationStatus.RELEASED);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.release(reservation.getId(), "mechanic"))
                .isInstanceOf(ReservationStateException.class);
    }

    @Test
    void gettingAnUnknownReservationThrows() {
        UUID id = UUID.randomUUID();
        when(reservationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.getById(id))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void getByIdReturnsTheReservation() {
        Part part = TestFixtures.part("BRK-1", 20, 5, 2);
        PartReservation reservation = TestFixtures.reservation(part, 5, ReservationStatus.RESERVED);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThat(reservationService.getById(reservation.getId()).sku()).isEqualTo("BRK-1");
    }

    @Test
    void findForRepairOrderMapsEveryReservation() {
        Part part = TestFixtures.part("BRK-1", 20, 5, 2);
        UUID orderId = UUID.randomUUID();
        when(reservationRepository.findAllByRepairOrderIdOrderByCreatedAtAsc(orderId))
                .thenReturn(List.of(TestFixtures.reservation(part, 2, ReservationStatus.RESERVED)));

        assertThat(reservationService.findForRepairOrder(orderId)).hasSize(1);
    }

    @Test
    void expiringStaleReservationsReturnsThemToStock() {
        Part part = TestFixtures.part("BRK-1", 20, 4, 2);
        PartReservation stale = TestFixtures.reservation(part, 4, ReservationStatus.RESERVED);
        stale.setCreatedAt(LocalDateTime.now().minusDays(5));
        when(reservationRepository.findAllByStatusAndCreatedAtBefore(
                org.mockito.ArgumentMatchers.eq(ReservationStatus.RESERVED), any(LocalDateTime.class)))
                .thenReturn(List.of(stale));
        when(reservationRepository.save(any(PartReservation.class)))
                .thenAnswer(call -> call.getArgument(0));

        int expired = reservationService.expireStaleReservations(Duration.ofHours(48), "expiry");

        assertThat(expired).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(part.getQuantityReserved()).isZero();
    }

    @Test
    void expiringWhenNothingIsStaleChangesNothing() {
        when(reservationRepository.findAllByStatusAndCreatedAtBefore(
                org.mockito.ArgumentMatchers.eq(ReservationStatus.RESERVED), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertThat(reservationService.expireStaleReservations(Duration.ofHours(48), "expiry")).isZero();
    }
}
