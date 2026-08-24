package bg.softuni.garage.common.export;

import java.util.UUID;

public interface InvoiceService {

    InvoiceDocument renderInvoice(UUID orderId, UUID viewerId, boolean staffView);
}
