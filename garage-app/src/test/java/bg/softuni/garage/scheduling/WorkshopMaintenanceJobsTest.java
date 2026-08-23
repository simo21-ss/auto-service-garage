package bg.softuni.garage.scheduling;

import bg.softuni.garage.TestFixtures;
import bg.softuni.garage.common.audit.AuditService;
import bg.softuni.garage.common.scheduling.WorkshopMaintenanceJobs;
import bg.softuni.garage.mechanic.Specialty;
import bg.softuni.garage.parts.PartsCatalogService;
import bg.softuni.garage.parts.dto.PartView;
import bg.softuni.garage.repairorder.RepairOrder;
import bg.softuni.garage.repairorder.RepairOrderRepository;
import bg.softuni.garage.repairorder.RepairOrderStatus;
import bg.softuni.garage.user.RoleName;
import bg.softuni.garage.user.User;
import bg.softuni.garage.vehicle.Vehicle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
class WorkshopMaintenanceJobsTest {

    @Mock
    private RepairOrderRepository repairOrderRepository;

    @Mock
    private PartsCatalogService partsCatalogService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private WorkshopMaintenanceJobs jobs;

    @Test
    void theOverdueSweepRecordsEveryOrderPastItsSlot() {
        when(repairOrderRepository.findOverdue(eq(RepairOrderStatus.SCHEDULED), any(LocalDateTime.class)))
                .thenReturn(List.of(order()));

        jobs.flagOverdueRepairOrders();

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq("OVERDUE_SWEEP"), eq("scheduler"), details.capture());
        assertThat(details.getValue()).contains("RO-2026-0001");
    }

    @Test
    void theOverdueSweepStaysQuietWhenNothingIsLate() {
        when(repairOrderRepository.findOverdue(eq(RepairOrderStatus.SCHEDULED), any(LocalDateTime.class)))
                .thenReturn(List.of());

        jobs.flagOverdueRepairOrders();

        verify(auditService, never()).record(anyString(), anyString(), anyString());
    }

    @Test
    void theLowStockCheckRecordsDepletedParts() {
        when(partsCatalogService.lowStock()).thenReturn(List.of(part("BRK-1")));

        jobs.reportLowStock();

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq("LOW_STOCK_ALERT"), eq("scheduler"), details.capture());
        assertThat(details.getValue()).contains("BRK-1");
    }

    @Test
    void theLowStockCheckStaysQuietWhenStockIsHealthy() {
        when(partsCatalogService.lowStock()).thenReturn(List.of());

        jobs.reportLowStock();

        verify(auditService, never()).record(anyString(), anyString(), anyString());
    }

    @Test
    void theAuditPurgeDelegatesToTheAuditService() {
        jobs.purgeOldAuditEntries();

        verify(auditService).purgeOlderThan(any(LocalDateTime.class));
    }

    private RepairOrder order() {
        User owner = TestFixtures.user("ivan", RoleName.CUSTOMER);
        Vehicle vehicle = TestFixtures.vehicle("CB 1234 AB", owner);
        RepairOrder order = TestFixtures.order(vehicle, RepairOrderStatus.SCHEDULED, Specialty.SUSPENSION);
        order.setScheduledAt(LocalDateTime.now().minusDays(1));
        return order;
    }

    private PartView part(String sku) {
        return new PartView(UUID.randomUUID(), sku, "Part " + sku, "BRAKES",
                new BigDecimal("50.00"), 2, 0, 2, 5, true, "Bosch Bulgaria");
    }
}
