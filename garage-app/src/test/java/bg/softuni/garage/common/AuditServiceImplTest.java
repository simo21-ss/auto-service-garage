package bg.softuni.garage.common;

import bg.softuni.garage.common.audit.AuditEntry;
import bg.softuni.garage.common.audit.AuditEntryRepository;
import bg.softuni.garage.common.audit.AuditServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private AuditEntryRepository auditEntryRepository;

    @InjectMocks
    private AuditServiceImpl auditService;

    @Test
    void recordStoresTheEntryWithATimestamp() {
        auditService.record("ORDER_COMPLETED", "workshop", "RO-2026-0001 invoiced");

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());

        AuditEntry saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("ORDER_COMPLETED");
        assertThat(saved.getActor()).isEqualTo("workshop");
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void overlongDetailsAreTruncatedToFitTheColumn() {
        auditService.record("ORDER_COMPLETED", "workshop", "x".repeat(900));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());

        assertThat(captor.getValue().getDetails()).hasSize(500);
    }

    @Test
    void nullDetailsAreStoredAsIs() {
        auditService.record("ORDER_COMPLETED", "workshop", null);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());

        assertThat(captor.getValue().getDetails()).isNull();
    }

    @Test
    void findRecentAsksTheRepositoryForALimitedSlice() {
        when(auditEntryRepository.findAllByOrderByOccurredAtDesc(Limit.of(10)))
                .thenReturn(List.of(new AuditEntry()));

        assertThat(auditService.findRecent(10)).hasSize(1);
    }

    @Test
    void purgeReportsHowManyEntriesWereRemoved() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        when(auditEntryRepository.deleteByOccurredAtBefore(cutoff)).thenReturn(7L);

        assertThat(auditService.purgeOlderThan(cutoff)).isEqualTo(7L);
    }

    @Test
    void purgingNothingIsSilent() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        when(auditEntryRepository.deleteByOccurredAtBefore(cutoff)).thenReturn(0L);

        assertThat(auditService.purgeOlderThan(cutoff)).isZero();
    }
}
