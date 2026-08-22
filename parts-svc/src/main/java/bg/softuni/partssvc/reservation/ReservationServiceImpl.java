package bg.softuni.partssvc.reservation;

import bg.softuni.partssvc.common.event.StockDepletedEvent;
import bg.softuni.partssvc.common.exception.InsufficientStockException;
import bg.softuni.partssvc.common.exception.ReservationNotFoundException;
import bg.softuni.partssvc.common.exception.ReservationStateException;
import bg.softuni.partssvc.config.CacheConfig;
import bg.softuni.partssvc.ledger.StockLedgerService;
import bg.softuni.partssvc.part.Part;
import bg.softuni.partssvc.part.PartRepository;
import bg.softuni.partssvc.part.PartService;
import bg.softuni.partssvc.reservation.dto.ReservationRequest;
import bg.softuni.partssvc.reservation.dto.ReservationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ReservationServiceImpl implements ReservationService {

    private static final String REASON_CONSUMED = "CONSUMED";

    private final PartReservationRepository reservationRepository;
    private final PartRepository partRepository;
    private final PartService partService;
    private final StockLedgerService stockLedgerService;
    private final ApplicationEventPublisher eventPublisher;

    public ReservationServiceImpl(PartReservationRepository reservationRepository,
                                  PartRepository partRepository,
                                  PartService partService,
                                  StockLedgerService stockLedgerService,
                                  ApplicationEventPublisher eventPublisher) {
        this.reservationRepository = reservationRepository;
        this.partRepository = partRepository;
        this.partService = partService;
        this.stockLedgerService = stockLedgerService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PART_CATALOGUE, allEntries = true),
            @CacheEvict(value = CacheConfig.LOW_STOCK, allEntries = true)
    })
    public ReservationResponse reserve(ReservationRequest request, String actor) {
        Part part = partService.getEntityBySku(request.sku());
        int available = part.availableQuantity();

        if (request.quantity() > available) {
            throw new InsufficientStockException(part.getSku(), request.quantity(), available);
        }

        part.setQuantityReserved(part.getQuantityReserved() + request.quantity());
        partRepository.save(part);

        PartReservation reservation = new PartReservation();
        reservation.setRepairOrderId(request.repairOrderId());
        reservation.setPart(part);
        reservation.setQuantity(request.quantity());
        reservation.setUnitPrice(part.getUnitPrice());
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setCreatedAt(LocalDateTime.now());

        PartReservation saved = reservationRepository.save(reservation);
        publishIfDepleted(part);

        log.info("Reserved {} x {} for repair order {} ({} left available)",
                saved.getQuantity(), part.getSku(), saved.getRepairOrderId(), part.availableQuantity());
        return toResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PART_CATALOGUE, allEntries = true),
            @CacheEvict(value = CacheConfig.LOW_STOCK, allEntries = true)
    })
    public ReservationResponse consume(UUID reservationId, String actor) {
        PartReservation reservation = requireReserved(reservationId, "consumed");
        Part part = reservation.getPart();

        int onHandBefore = part.getQuantityOnHand();
        part.setQuantityOnHand(onHandBefore - reservation.getQuantity());
        part.setQuantityReserved(part.getQuantityReserved() - reservation.getQuantity());
        partRepository.save(part);

        reservation.setStatus(ReservationStatus.CONSUMED);
        reservation.setResolvedAt(LocalDateTime.now());

        PartReservation saved = reservationRepository.save(reservation);
        stockLedgerService.record(part.getSku(), REASON_CONSUMED, actor,
                -reservation.getQuantity(), onHandBefore);

        log.info("Consumed {} x {} on repair order {}, {} left on hand",
                saved.getQuantity(), part.getSku(), saved.getRepairOrderId(), part.getQuantityOnHand());
        return toResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PART_CATALOGUE, allEntries = true),
            @CacheEvict(value = CacheConfig.LOW_STOCK, allEntries = true)
    })
    public ReservationResponse release(UUID reservationId, String actor) {
        PartReservation reservation = requireReserved(reservationId, "released");
        PartReservation saved = returnToStock(reservation, ReservationStatus.RELEASED);

        log.info("Released {} x {} back to stock from repair order {}",
                saved.getQuantity(), saved.getPart().getSku(), saved.getRepairOrderId());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getById(UUID reservationId) {
        return toResponse(loadReservation(reservationId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> findForRepairOrder(UUID repairOrderId) {
        return reservationRepository.findAllByRepairOrderIdOrderByCreatedAtAsc(repairOrderId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PART_CATALOGUE, allEntries = true),
            @CacheEvict(value = CacheConfig.LOW_STOCK, allEntries = true)
    })
    public int expireStaleReservations(Duration olderThan, String actor) {
        LocalDateTime cutoff = LocalDateTime.now().minus(olderThan);
        List<PartReservation> stale = reservationRepository
                .findAllByStatusAndCreatedAtBefore(ReservationStatus.RESERVED, cutoff);

        stale.forEach(reservation -> returnToStock(reservation, ReservationStatus.EXPIRED));

        if (!stale.isEmpty()) {
            log.info("Expired {} stale reservation(s) older than {}", stale.size(), olderThan);
        }
        return stale.size();
    }

    private void publishIfDepleted(Part part) {
        if (part.availableQuantity() <= part.getReorderLevel()) {
            eventPublisher.publishEvent(new StockDepletedEvent(part.getSku(),
                    part.getName(),
                    part.availableQuantity(),
                    part.getReorderLevel(),
                    part.getSupplier().getName()));
        }
    }

    private PartReservation returnToStock(PartReservation reservation, ReservationStatus status) {
        Part part = reservation.getPart();
        part.setQuantityReserved(part.getQuantityReserved() - reservation.getQuantity());
        partRepository.save(part);

        reservation.setStatus(status);
        reservation.setResolvedAt(LocalDateTime.now());
        return reservationRepository.save(reservation);
    }

    private PartReservation requireReserved(UUID reservationId, String action) {
        PartReservation reservation = loadReservation(reservationId);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new ReservationStateException("A " + reservation.getStatus()
                    + " reservation cannot be " + action);
        }
        return reservation;
    }

    private PartReservation loadReservation(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(
                        "No reservation with id " + reservationId));
    }

    private ReservationResponse toResponse(PartReservation reservation) {
        return new ReservationResponse(reservation.getId(),
                reservation.getRepairOrderId(),
                reservation.getPart().getSku(),
                reservation.getPart().getName(),
                reservation.getQuantity(),
                reservation.getUnitPrice(),
                reservation.lineTotal(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getResolvedAt());
    }
}
