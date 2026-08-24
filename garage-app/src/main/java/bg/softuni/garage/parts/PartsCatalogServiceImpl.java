package bg.softuni.garage.parts;

import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.parts.dto.PartView;
import bg.softuni.garage.parts.dto.ReservationCommand;
import bg.softuni.garage.parts.dto.ReservationView;
import bg.softuni.garage.parts.dto.RestockCommand;
import bg.softuni.garage.config.CacheConfig;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class PartsCatalogServiceImpl implements PartsCatalogService {

    private static final String RESERVED_STATUS = "RESERVED";
    private static final String CONSUMED_STATUS = "CONSUMED";

    private final PartsClient partsClient;

    public PartsCatalogServiceImpl(PartsClient partsClient) {
        this.partsClient = partsClient;
    }

    @Override
    @Cacheable(value = CacheConfig.PARTS_CATALOGUE, unless = "#result.isEmpty()")
    public List<PartView> catalogue() {
        try {
            return partsClient.catalogue().parts();
        } catch (FeignException exception) {
            log.error("Parts catalogue is unavailable: {}", exception.getMessage());
            return List.of();
        }
    }

    @Override
    @Cacheable(value = CacheConfig.LOW_STOCK_PARTS, unless = "#result.isEmpty()")
    public List<PartView> lowStock() {
        try {
            return partsClient.lowStock().parts();
        } catch (FeignException exception) {
            log.error("Low stock report is unavailable: {}", exception.getMessage());
            return List.of();
        }
    }

    @Override
    public List<ReservationView> reservationsFor(UUID repairOrderId) {
        try {
            return partsClient.reservationsFor(repairOrderId).reservations();
        } catch (FeignException exception) {
            log.error("Reservations for repair order {} are unavailable: {}",
                    repairOrderId, exception.getMessage());
            return List.of();
        }
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PARTS_CATALOGUE, allEntries = true),
            @CacheEvict(value = CacheConfig.LOW_STOCK_PARTS, allEntries = true)
    })
    public ReservationView reserve(UUID repairOrderId, String sku, int quantity) {
        ReservationView reservation =
                partsClient.reserve(new ReservationCommand(repairOrderId, sku, quantity));

        log.info("Reserved {} x {} from the parts service for repair order {}",
                quantity, sku, repairOrderId);
        return reservation;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PARTS_CATALOGUE, allEntries = true),
            @CacheEvict(value = CacheConfig.LOW_STOCK_PARTS, allEntries = true)
    })
    public void release(UUID reservationId) {
        ReservationView released = partsClient.release(reservationId);
        log.info("Released reservation {} ({} x {}) back to stock",
                reservationId, released.quantity(), released.sku());
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PARTS_CATALOGUE, allEntries = true),
            @CacheEvict(value = CacheConfig.LOW_STOCK_PARTS, allEntries = true)
    })
    public BigDecimal consumeAllFor(UUID repairOrderId) {
        try {
            List<ReservationView> reserved = openReservations(repairOrderId);
            reserved.forEach(reservation -> partsClient.consume(reservation.id()));

            BigDecimal total = partsClient.reservationsFor(repairOrderId).reservations().stream()
                    .filter(reservation -> CONSUMED_STATUS.equals(reservation.status()))
                    .map(ReservationView::lineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            log.info("Consumed {} reservation(s), total parts value {} for repair order {}",
                    reserved.size(), total, repairOrderId);
            return total;
        } catch (FeignException exception) {
            log.error("Could not consume parts for repair order {}: {}",
                    repairOrderId, exception.getMessage());
            throw new BusinessRuleException(
                    "The parts service is unavailable, so this order cannot be completed right now");
        }
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PARTS_CATALOGUE, allEntries = true),
            @CacheEvict(value = CacheConfig.LOW_STOCK_PARTS, allEntries = true)
    })
    public int releaseAllFor(UUID repairOrderId) {
        try {
            List<ReservationView> reserved = openReservations(repairOrderId);
            reserved.forEach(reservation -> partsClient.release(reservation.id()));

            if (!reserved.isEmpty()) {
                log.info("Released {} reservation(s) for cancelled repair order {}",
                        reserved.size(), repairOrderId);
            }
            return reserved.size();
        } catch (FeignException exception) {
            log.error("Could not release parts for repair order {}, they will expire automatically: {}",
                    repairOrderId, exception.getMessage());
            return 0;
        }
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PARTS_CATALOGUE, allEntries = true),
            @CacheEvict(value = CacheConfig.LOW_STOCK_PARTS, allEntries = true)
    })
    public PartView restock(UUID partId, int quantity, String note) {
        PartView part = partsClient.restock(partId, new RestockCommand(quantity, note));
        log.info("Restocked {} by {} unit(s), now {} on hand",
                part.sku(), quantity, part.quantityOnHand());
        return part;
    }

    private List<ReservationView> openReservations(UUID repairOrderId) {
        return partsClient.reservationsFor(repairOrderId).reservations().stream()
                .filter(reservation -> RESERVED_STATUS.equals(reservation.status()))
                .toList();
    }
}
