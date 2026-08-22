package bg.softuni.garage.common.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class AuditServiceImpl implements AuditService {

    private static final int MAX_DETAILS_LENGTH = 500;

    private final AuditEntryRepository auditEntryRepository;

    public AuditServiceImpl(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String actor, String details) {
        AuditEntry entry = new AuditEntry();
        entry.setAction(action);
        entry.setActor(actor);
        entry.setDetails(trim(details));
        entry.setOccurredAt(LocalDateTime.now());

        auditEntryRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntry> findRecent(int limit) {
        return auditEntryRepository.findAllByOrderByOccurredAtDesc(Limit.of(limit));
    }

    @Override
    @Transactional
    public long purgeOlderThan(LocalDateTime cutoff) {
        long removed = auditEntryRepository.deleteByOccurredAtBefore(cutoff);
        if (removed > 0) {
            log.info("Purged {} audit entries older than {}", removed, cutoff);
        }
        return removed;
    }

    private String trim(String details) {
        if (details == null || details.length() <= MAX_DETAILS_LENGTH) {
            return details;
        }
        return details.substring(0, MAX_DETAILS_LENGTH);
    }
}
