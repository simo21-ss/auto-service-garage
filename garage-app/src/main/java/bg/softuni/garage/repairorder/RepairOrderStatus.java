package bg.softuni.garage.repairorder;

import java.util.List;

public enum RepairOrderStatus {
    REQUESTED,
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public static List<RepairOrderStatus> openStatuses() {
        return List.of(REQUESTED, SCHEDULED, IN_PROGRESS);
    }
}
