package bg.softuni.partssvc.ledger;

import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StockLedgerRepository extends MongoRepository<StockLedgerEntry, String> {

    List<StockLedgerEntry> findAllBySkuOrderByOccurredAtDesc(String sku, Limit limit);

    List<StockLedgerEntry> findAllByOrderByOccurredAtDesc(Limit limit);
}
