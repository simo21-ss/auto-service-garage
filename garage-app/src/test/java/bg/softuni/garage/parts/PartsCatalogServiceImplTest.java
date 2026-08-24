package bg.softuni.garage.parts;

import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.parts.dto.PartCollection;
import bg.softuni.garage.parts.dto.PartView;
import bg.softuni.garage.parts.dto.ReservationCollection;
import bg.softuni.garage.parts.dto.ReservationCommand;
import bg.softuni.garage.parts.dto.ReservationView;
import bg.softuni.garage.parts.dto.RestockCommand;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartsCatalogServiceImplTest {

    @Mock
    private PartsClient partsClient;

    @InjectMocks
    private PartsCatalogServiceImpl partsCatalogService;

    @Test
    void catalogueReturnsThePartsFromTheService() {
        when(partsClient.catalogue()).thenReturn(collectionOf(part("BRK-1", 20)));

        assertThat(partsCatalogService.catalogue()).singleElement()
                .satisfies(part -> assertThat(part.sku()).isEqualTo("BRK-1"));
    }

    @Test
    void anUnreachableServiceDegradesToAnEmptyCatalogue() {
        when(partsClient.catalogue()).thenThrow(unavailable());

        assertThat(partsCatalogService.catalogue()).isEmpty();
    }

    @Test
    void anEmptyCatalogueIsNeverCachedSoAnOutageDoesNotPersist() throws Exception {
        var method = PartsCatalogServiceImpl.class.getMethod("catalogue");
        var cacheable = method.getAnnotation(org.springframework.cache.annotation.Cacheable.class);

        assertThat(cacheable.unless()).isEqualTo("#result.isEmpty()");
    }

    @Test
    void anEmptyLowStockReportIsNeverCachedEither() throws Exception {
        var method = PartsCatalogServiceImpl.class.getMethod("lowStock");
        var cacheable = method.getAnnotation(org.springframework.cache.annotation.Cacheable.class);

        assertThat(cacheable.unless()).isEqualTo("#result.isEmpty()");
    }

    @Test
    void anUnreachableServiceDegradesToAnEmptyLowStockReport() {
        when(partsClient.lowStock()).thenThrow(unavailable());

        assertThat(partsCatalogService.lowStock()).isEmpty();
    }

    @Test
    void lowStockReturnsTheDepletedParts() {
        when(partsClient.lowStock()).thenReturn(collectionOf(part("BRK-1", 1)));

        assertThat(partsCatalogService.lowStock()).hasSize(1);
    }

    @Test
    void reservationsForAnOrderDegradeToAnEmptyList() {
        UUID orderId = UUID.randomUUID();
        when(partsClient.reservationsFor(orderId)).thenThrow(unavailable());

        assertThat(partsCatalogService.reservationsFor(orderId)).isEmpty();
    }

    @Test
    void reserveDelegatesToTheClient() {
        UUID orderId = UUID.randomUUID();
        when(partsClient.reserve(any(ReservationCommand.class)))
                .thenReturn(reservation("BRK-1", 2, "RESERVED"));

        assertThat(partsCatalogService.reserve(orderId, "BRK-1", 2).sku()).isEqualTo("BRK-1");
        verify(partsClient).reserve(new ReservationCommand(orderId, "BRK-1", 2));
    }

    @Test
    void consumeAllBillsEveryReservationTheOrderHasConsumed() {
        UUID orderId = UUID.randomUUID();
        ReservationView open = reservation("BRK-1", 2, "RESERVED");
        ReservationView alreadyDone = reservation("BRK-2", 1, "CONSUMED");

        when(partsClient.reservationsFor(orderId))
                .thenReturn(new ReservationCollection(
                        new ReservationCollection.Embedded(List.of(open, alreadyDone))))
                .thenReturn(new ReservationCollection(
                        new ReservationCollection.Embedded(List.of(
                                reservation("BRK-1", 2, "CONSUMED"), alreadyDone))));
        when(partsClient.consume(open.id())).thenReturn(reservation("BRK-1", 2, "CONSUMED"));

        BigDecimal total = partsCatalogService.consumeAllFor(orderId);

        assertThat(total).isEqualByComparingTo("150.00");
        verify(partsClient, times(1)).consume(any(UUID.class));
    }

    @Test
    void aRepeatedCompletionStillBillsThePartsAlreadyConsumed() {
        UUID orderId = UUID.randomUUID();
        ReservationView consumed = reservation("BRK-1", 2, "CONSUMED");

        when(partsClient.reservationsFor(orderId))
                .thenReturn(new ReservationCollection(
                        new ReservationCollection.Embedded(List.of(consumed))));

        BigDecimal total = partsCatalogService.consumeAllFor(orderId);

        assertThat(total).isEqualByComparingTo("100.00");
        verify(partsClient, never()).consume(any(UUID.class));
    }

    @Test
    void completingIsRefusedWhenThePartsServiceIsDown() {
        UUID orderId = UUID.randomUUID();
        when(partsClient.reservationsFor(orderId)).thenThrow(unavailable());

        assertThatThrownBy(() -> partsCatalogService.consumeAllFor(orderId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("parts service is unavailable");
    }

    @Test
    void releaseAllReturnsEveryOpenReservation() {
        UUID orderId = UUID.randomUUID();
        ReservationView open = reservation("BRK-1", 2, "RESERVED");
        when(partsClient.reservationsFor(orderId))
                .thenReturn(new ReservationCollection(
                        new ReservationCollection.Embedded(List.of(open))));

        assertThat(partsCatalogService.releaseAllFor(orderId)).isEqualTo(1);
        verify(partsClient).release(open.id());
    }

    @Test
    void cancellingStillSucceedsWhenThePartsServiceIsDown() {
        UUID orderId = UUID.randomUUID();
        when(partsClient.reservationsFor(orderId)).thenThrow(unavailable());

        assertThat(partsCatalogService.releaseAllFor(orderId)).isZero();
        verify(partsClient, never()).release(any(UUID.class));
    }

    @Test
    void releaseDelegatesToTheClient() {
        ReservationView released = reservation("BRK-1", 2, "RELEASED");
        when(partsClient.release(released.id())).thenReturn(released);

        partsCatalogService.release(released.id());

        verify(partsClient).release(released.id());
    }

    @Test
    void restockDelegatesToTheClient() {
        UUID partId = UUID.randomUUID();
        when(partsClient.restock(any(UUID.class), any(RestockCommand.class)))
                .thenReturn(part("BRK-1", 30));

        assertThat(partsCatalogService.restock(partId, 10, "delivery").quantityOnHand()).isEqualTo(30);
    }

    private PartCollection collectionOf(PartView... parts) {
        return new PartCollection(new PartCollection.Embedded(List.of(parts)));
    }

    private PartView part(String sku, int onHand) {
        return new PartView(UUID.randomUUID(), sku, "Part " + sku, "BRAKES",
                new BigDecimal("50.00"), onHand, 0, onHand, 5, false, "Bosch Bulgaria");
    }

    private ReservationView reservation(String sku, int quantity, String status) {
        return new ReservationView(UUID.randomUUID(), UUID.randomUUID(), sku, "Part " + sku,
                quantity, new BigDecimal("50.00"),
                new BigDecimal("50.00").multiply(BigDecimal.valueOf(quantity)),
                status, LocalDateTime.now(), null);
    }

    private FeignException unavailable() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/parts", Map.of(),
                new byte[0], StandardCharsets.UTF_8, new RequestTemplate());
        return new FeignException.ServiceUnavailable("parts service is down", request, null, Map.of());
    }
}
