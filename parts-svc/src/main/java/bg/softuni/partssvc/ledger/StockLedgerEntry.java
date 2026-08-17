package bg.softuni.partssvc.ledger;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "stock_ledger")
@Getter
@Setter
public class StockLedgerEntry {

    @Id
    private String id;

    @Indexed
    private String sku;

    private String reason;

    private String actor;

    private int delta;

    private int quantityBefore;

    private int quantityAfter;

    @Indexed
    private Instant occurredAt;
}
