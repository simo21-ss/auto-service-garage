package bg.softuni.partssvc.common.event;

public record StockDepletedEvent(String sku,
                                 String partName,
                                 int quantityAvailable,
                                 int reorderLevel,
                                 String supplierName) {
}
