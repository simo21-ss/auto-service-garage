package bg.softuni.garage.common.event;

import bg.softuni.garage.common.audit.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class RepairOrderEventListener {

    private static final String ACTION_COMPLETED = "REPAIR_ORDER_COMPLETED";
    private static final String ACTION_CANCELLED = "REPAIR_ORDER_CANCELLED";
    private static final String SYSTEM_ACTOR = "workshop";

    private final AuditService auditService;

    public RepairOrderEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @TransactionalEventListener
    public void onCompleted(RepairOrderCompletedEvent event) {
        auditService.record(ACTION_COMPLETED, actorOf(event.mechanicName()),
                "%s on %s invoiced at %s BGN".formatted(
                        event.reference(), event.plate(), event.totalCost()));

        log.info("Audit trail updated for completed repair order {}", event.reference());
    }

    @EventListener
    public void onCancelled(RepairOrderCancelledEvent event) {
        auditService.record(ACTION_CANCELLED, event.cancelledByStaff() ? SYSTEM_ACTOR : "customer",
                "%s on %s cancelled, %d part reservation(s) returned to stock".formatted(
                        event.reference(), event.plate(), event.releasedReservations()));

        log.info("Audit trail updated for cancelled repair order {}", event.reference());
    }

    private String actorOf(String mechanicName) {
        return mechanicName == null ? SYSTEM_ACTOR : mechanicName;
    }
}
