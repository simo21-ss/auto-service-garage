package bg.softuni.garage.parts;

import bg.softuni.garage.parts.dto.PartView;
import bg.softuni.garage.parts.dto.ReservationView;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PartsCatalogService {

    List<PartView> catalogue();

    List<PartView> lowStock();

    List<ReservationView> reservationsFor(UUID repairOrderId);

    ReservationView reserve(UUID repairOrderId, String sku, int quantity);

    void release(UUID reservationId);

    BigDecimal consumeAllFor(UUID repairOrderId);

    int releaseAllFor(UUID repairOrderId);

    PartView restock(UUID partId, int quantity, String note);
}
