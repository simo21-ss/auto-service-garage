package bg.softuni.partssvc.ledger;

import bg.softuni.partssvc.ledger.dto.LedgerEntryResponse;

import java.util.List;

public interface StockLedgerService {

    void record(String sku, String reason, String actor, int delta, int quantityBefore);

    List<LedgerEntryResponse> findRecentForSku(String sku, int limit);

    List<LedgerEntryResponse> findRecent(int limit);
}
