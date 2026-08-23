package bg.softuni.partssvc;

import bg.softuni.partssvc.part.Part;
import bg.softuni.partssvc.part.PartCategory;
import bg.softuni.partssvc.reservation.PartReservation;
import bg.softuni.partssvc.reservation.ReservationStatus;
import bg.softuni.partssvc.supplier.Supplier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static Supplier supplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setId(UUID.randomUUID());
        supplier.setName(name);
        supplier.setEmail("orders@" + name.toLowerCase().replace(" ", "") + ".bg");
        supplier.setLeadTimeDays(5);
        supplier.setActive(true);
        return supplier;
    }

    public static Part part(String sku, int onHand, int reserved, int reorderLevel) {
        Part part = new Part();
        part.setId(UUID.randomUUID());
        part.setSku(sku);
        part.setName("Part " + sku);
        part.setCategory(PartCategory.BRAKES);
        part.setUnitPrice(new BigDecimal("50.00"));
        part.setQuantityOnHand(onHand);
        part.setQuantityReserved(reserved);
        part.setReorderLevel(reorderLevel);
        part.setReorderQuantity(20);
        part.setSupplier(supplier("Bosch Bulgaria"));
        return part;
    }

    public static PartReservation reservation(Part part, int quantity, ReservationStatus status) {
        PartReservation reservation = new PartReservation();
        reservation.setId(UUID.randomUUID());
        reservation.setRepairOrderId(UUID.randomUUID());
        reservation.setPart(part);
        reservation.setQuantity(quantity);
        reservation.setUnitPrice(part.getUnitPrice());
        reservation.setStatus(status);
        reservation.setCreatedAt(LocalDateTime.now());
        return reservation;
    }
}
