package bg.softuni.partssvc.ledger;

import bg.softuni.partssvc.ledger.dto.LedgerEntryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class StockLedgerServiceImpl implements StockLedgerService {

    private final StockLedgerRepository stockLedgerRepository;

    public StockLedgerServiceImpl(StockLedgerRepository stockLedgerRepository) {
        this.stockLedgerRepository = stockLedgerRepository;
    }

    @Override
    public void record(String sku, String reason, String actor, int delta, int quantityBefore) {
        StockLedgerEntry entry = new StockLedgerEntry();
        entry.setSku(sku);
        entry.setReason(reason);
        entry.setActor(actor);
        entry.setDelta(delta);
        entry.setQuantityBefore(quantityBefore);
        entry.setQuantityAfter(quantityBefore + delta);
        entry.setOccurredAt(Instant.now());

        try {
            stockLedgerRepository.save(entry);
            log.info("Ledger: {} {}{} for {} ({} -> {})",
                    sku, delta >= 0 ? "+" : "", delta, reason,
                    entry.getQuantityBefore(), entry.getQuantityAfter());
        } catch (DataAccessException exception) {
            log.error("Could not append to the stock ledger for {}: {}", sku, exception.getMessage());
        }
    }

    @Override
    public List<LedgerEntryResponse> findRecentForSku(String sku, int limit) {
        return stockLedgerRepository.findAllBySkuOrderByOccurredAtDesc(sku, Limit.of(limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<LedgerEntryResponse> findRecent(int limit) {
        return stockLedgerRepository.findAllByOrderByOccurredAtDesc(Limit.of(limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    private LedgerEntryResponse toResponse(StockLedgerEntry entry) {
        return new LedgerEntryResponse(entry.getId(),
                entry.getSku(),
                entry.getReason(),
                entry.getActor(),
                entry.getDelta(),
                entry.getQuantityBefore(),
                entry.getQuantityAfter(),
                entry.getOccurredAt());
    }
}
