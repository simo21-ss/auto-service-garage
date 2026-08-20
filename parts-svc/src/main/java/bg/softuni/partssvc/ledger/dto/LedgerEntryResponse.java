package bg.softuni.partssvc.ledger.dto;

import java.time.Instant;

public record LedgerEntryResponse(String id,
                                  String sku,
                                  String reason,
                                  String actor,
                                  int delta,
                                  int quantityBefore,
                                  int quantityAfter,
                                  Instant occurredAt) {
}
