package bg.softuni.partssvc.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StockDepletedListener {

    @EventListener
    public void onStockDepleted(StockDepletedEvent event) {
        log.warn("{} ('{}') has dropped to {} available against a reorder level of {}, "
                        + "a purchase order should go to {}",
                event.sku(), event.partName(), event.quantityAvailable(),
                event.reorderLevel(), event.supplierName());
    }
}
