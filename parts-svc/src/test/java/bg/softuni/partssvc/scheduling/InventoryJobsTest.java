package bg.softuni.partssvc.scheduling;

import bg.softuni.partssvc.TestFixtures;
import bg.softuni.partssvc.common.scheduling.InventoryJobs;
import bg.softuni.partssvc.part.Part;
import bg.softuni.partssvc.part.PartRepository;
import bg.softuni.partssvc.part.PartService;
import bg.softuni.partssvc.part.dto.RestockRequest;
import bg.softuni.partssvc.reservation.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryJobsTest {

    @Mock
    private PartRepository partRepository;

    @Mock
    private PartService partService;

    @Mock
    private ReservationService reservationService;

    @Test
    void autoReorderTopsUpEveryDepletedPart() {
        Part depleted = TestFixtures.part("BRK-1", 3, 0, 5);
        when(partRepository.findBelowReorderLevel()).thenReturn(List.of(depleted));

        jobs().reorderDepletedParts();

        ArgumentCaptor<RestockRequest> captor = ArgumentCaptor.forClass(RestockRequest.class);
        verify(partService).restock(eq(depleted.getId()), captor.capture(), anyString());
        assertThat(captor.getValue().quantity()).isEqualTo(depleted.getReorderQuantity());
    }

    @Test
    void autoReorderDoesNothingWhenStockIsHealthy() {
        when(partRepository.findBelowReorderLevel()).thenReturn(List.of());

        jobs().reorderDepletedParts();

        verify(partService, never()).restock(any(UUID.class), any(RestockRequest.class), anyString());
    }

    @Test
    void expirySweepUsesTheConfiguredReservationLifetime() {
        when(reservationService.expireStaleReservations(any(Duration.class), anyString())).thenReturn(2);

        jobs().expireStaleReservations();

        verify(reservationService).expireStaleReservations(Duration.ofHours(48), "reservation-expiry");
    }

    @Test
    void expirySweepHandlesAnEmptySweep() {
        when(reservationService.expireStaleReservations(any(Duration.class), anyString())).thenReturn(0);

        jobs().expireStaleReservations();

        verify(reservationService).expireStaleReservations(any(Duration.class), anyString());
    }

    private InventoryJobs jobs() {
        return new InventoryJobs(partRepository, partService, reservationService, 48);
    }
}
