package bg.softuni.partssvc.common.scheduling;

import bg.softuni.partssvc.part.Part;
import bg.softuni.partssvc.part.PartRepository;
import bg.softuni.partssvc.part.PartService;
import bg.softuni.partssvc.part.dto.RestockRequest;
import bg.softuni.partssvc.reservation.ReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class InventoryJobs {

    private static final String SCHEDULER_ACTOR = "auto-reorder";
    private static final String EXPIRY_ACTOR = "reservation-expiry";
    private static final String REORDER_NOTE = "Automatic reorder";

    private final PartRepository partRepository;
    private final PartService partService;
    private final ReservationService reservationService;
    private final Duration reservationTtl;

    public InventoryJobs(PartRepository partRepository,
                         PartService partService,
                         ReservationService reservationService,
                         @Value("${parts.jobs.reservation-ttl-hours:48}") long reservationTtlHours) {
        this.partRepository = partRepository;
        this.partService = partService;
        this.reservationService = reservationService;
        this.reservationTtl = Duration.ofHours(reservationTtlHours);
    }

    @Scheduled(cron = "${parts.jobs.auto-reorder-cron:0 0 2 * * *}")
    @Transactional
    public void reorderDepletedParts() {
        List<Part> depleted = partRepository.findBelowReorderLevel();

        if (depleted.isEmpty()) {
            log.info("Auto reorder found nothing below reorder level");
            return;
        }

        depleted.forEach(part -> partService.restock(part.getId(),
                new RestockRequest(part.getReorderQuantity(), REORDER_NOTE), SCHEDULER_ACTOR));

        log.info("Auto reorder topped up {} part(s) below reorder level", depleted.size());
    }

    @Scheduled(initialDelayString = "${parts.jobs.expiry-initial-delay-ms:30000}",
            fixedRateString = "${parts.jobs.expiry-interval-ms:600000}")
    public void expireStaleReservations() {
        int expired = reservationService.expireStaleReservations(reservationTtl, EXPIRY_ACTOR);

        if (expired == 0) {
            log.info("Reservation sweep found nothing older than {}", reservationTtl);
        }
    }
}
