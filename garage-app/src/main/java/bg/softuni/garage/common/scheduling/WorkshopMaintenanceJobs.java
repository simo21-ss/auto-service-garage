package bg.softuni.garage.common.scheduling;

import bg.softuni.garage.common.audit.AuditService;
import bg.softuni.garage.parts.PartsCatalogService;
import bg.softuni.garage.parts.dto.PartView;
import bg.softuni.garage.repairorder.RepairOrder;
import bg.softuni.garage.repairorder.RepairOrderRepository;
import bg.softuni.garage.repairorder.RepairOrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class WorkshopMaintenanceJobs {

    private static final String ACTION_OVERDUE_SWEEP = "OVERDUE_SWEEP";
    private static final String ACTION_LOW_STOCK_ALERT = "LOW_STOCK_ALERT";
    private static final String SCHEDULER_ACTOR = "scheduler";
    private static final int AUDIT_RETENTION_DAYS = 90;

    private final RepairOrderRepository repairOrderRepository;
    private final PartsCatalogService partsCatalogService;
    private final AuditService auditService;

    public WorkshopMaintenanceJobs(RepairOrderRepository repairOrderRepository,
                                   PartsCatalogService partsCatalogService,
                                   AuditService auditService) {
        this.repairOrderRepository = repairOrderRepository;
        this.partsCatalogService = partsCatalogService;
        this.auditService = auditService;
    }

    @Scheduled(cron = "${garage.jobs.overdue-sweep-cron:0 0 7 * * *}")
    @Transactional
    public void flagOverdueRepairOrders() {
        List<RepairOrder> overdue = repairOrderRepository.findOverdue(
                RepairOrderStatus.SCHEDULED, LocalDateTime.now());

        if (overdue.isEmpty()) {
            log.info("Overdue sweep found no scheduled orders past their slot");
            return;
        }

        String references = overdue.stream().map(RepairOrder::getReference).toList().toString();
        auditService.record(ACTION_OVERDUE_SWEEP, SCHEDULER_ACTOR,
                overdue.size() + " scheduled order(s) past their slot: " + references);

        log.warn("Overdue sweep flagged {} scheduled order(s) past their slot: {}",
                overdue.size(), references);
    }

    @Scheduled(cron = "${garage.jobs.audit-purge-cron:0 30 3 * * SUN}")
    public void purgeOldAuditEntries() {
        auditService.purgeOlderThan(LocalDateTime.now().minusDays(AUDIT_RETENTION_DAYS));
    }

    @Scheduled(initialDelayString = "${garage.jobs.low-stock-initial-delay-ms:60000}",
            fixedDelayString = "${garage.jobs.low-stock-interval-ms:900000}")
    public void reportLowStock() {
        List<PartView> lowStock = partsCatalogService.lowStock();

        if (lowStock.isEmpty()) {
            log.info("Low stock check found nothing below reorder level");
            return;
        }

        String skus = lowStock.stream().map(PartView::sku).toList().toString();
        auditService.record(ACTION_LOW_STOCK_ALERT, SCHEDULER_ACTOR,
                lowStock.size() + " part(s) below reorder level: " + skus);

        log.warn("Low stock check found {} part(s) below reorder level: {}", lowStock.size(), skus);
    }
}
