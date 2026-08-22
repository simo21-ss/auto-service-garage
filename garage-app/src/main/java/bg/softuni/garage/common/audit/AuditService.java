package bg.softuni.garage.common.audit;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditService {

    void record(String action, String actor, String details);

    List<AuditEntry> findRecent(int limit);

    long purgeOlderThan(LocalDateTime cutoff);
}
