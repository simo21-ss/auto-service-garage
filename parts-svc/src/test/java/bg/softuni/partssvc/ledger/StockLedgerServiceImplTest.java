package bg.softuni.partssvc.ledger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLedgerServiceImplTest {

    @Mock
    private StockLedgerRepository stockLedgerRepository;

    @InjectMocks
    private StockLedgerServiceImpl stockLedgerService;

    @Test
    void recordStoresTheMovementWithBeforeAndAfterQuantities() {
        stockLedgerService.record("BRK-1", "RESTOCK", "admin", 10, 5);

        ArgumentCaptor<StockLedgerEntry> captor = ArgumentCaptor.forClass(StockLedgerEntry.class);
        verify(stockLedgerRepository).save(captor.capture());

        StockLedgerEntry saved = captor.getValue();
        assertThat(saved.getSku()).isEqualTo("BRK-1");
        assertThat(saved.getDelta()).isEqualTo(10);
        assertThat(saved.getQuantityBefore()).isEqualTo(5);
        assertThat(saved.getQuantityAfter()).isEqualTo(15);
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void recordHandlesNegativeMovements() {
        stockLedgerService.record("BRK-1", "CONSUMED", "mechanic", -4, 20);

        ArgumentCaptor<StockLedgerEntry> captor = ArgumentCaptor.forClass(StockLedgerEntry.class);
        verify(stockLedgerRepository).save(captor.capture());

        assertThat(captor.getValue().getQuantityAfter()).isEqualTo(16);
    }

    @Test
    void aLedgerOutageNeverBreaksTheStockOperation() {
        when(stockLedgerRepository.save(any(StockLedgerEntry.class)))
                .thenThrow(new DataAccessResourceFailureException("mongo is down"));

        assertThatCode(() -> stockLedgerService.record("BRK-1", "RESTOCK", "admin", 10, 5))
                .doesNotThrowAnyException();
    }

    @Test
    void findRecentForSkuMapsEntriesOntoResponses() {
        when(stockLedgerRepository.findAllBySkuOrderByOccurredAtDesc("BRK-1", Limit.of(10)))
                .thenReturn(List.of(entry()));

        assertThat(stockLedgerService.findRecentForSku("BRK-1", 10))
                .singleElement()
                .satisfies(response -> assertThat(response.sku()).isEqualTo("BRK-1"));
    }

    @Test
    void findRecentMapsEntriesOntoResponses() {
        when(stockLedgerRepository.findAllByOrderByOccurredAtDesc(Limit.of(5)))
                .thenReturn(List.of(entry()));

        assertThat(stockLedgerService.findRecent(5)).hasSize(1);
    }

    private StockLedgerEntry entry() {
        StockLedgerEntry entry = new StockLedgerEntry();
        entry.setId("1");
        entry.setSku("BRK-1");
        entry.setReason("RESTOCK");
        entry.setActor("admin");
        entry.setDelta(10);
        entry.setQuantityBefore(5);
        entry.setQuantityAfter(15);
        entry.setOccurredAt(Instant.now());
        return entry;
    }
}
