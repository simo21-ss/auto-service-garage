package bg.softuni.partssvc.common.exception;

import lombok.Getter;

@Getter
public class InsufficientStockException extends RuntimeException {

    private final String sku;
    private final int requested;
    private final int available;

    public InsufficientStockException(String sku, int requested, int available) {
        super("Only %d unit(s) of %s are available, %d were requested"
                .formatted(available, sku, requested));
        this.sku = sku;
        this.requested = requested;
        this.available = available;
    }
}
