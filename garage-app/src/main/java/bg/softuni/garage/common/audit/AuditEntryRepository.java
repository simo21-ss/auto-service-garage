package bg.softuni.garage.common.audit;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    List<AuditEntry> findAllByOrderByOccurredAtDesc(Limit limit);

    long deleteByOccurredAtBefore(LocalDateTime cutoff);
}
