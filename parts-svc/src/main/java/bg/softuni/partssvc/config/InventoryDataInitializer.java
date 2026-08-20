package bg.softuni.partssvc.config;

import bg.softuni.partssvc.ledger.StockLedgerService;
import bg.softuni.partssvc.part.Part;
import bg.softuni.partssvc.part.PartCategory;
import bg.softuni.partssvc.part.PartRepository;
import bg.softuni.partssvc.supplier.Supplier;
import bg.softuni.partssvc.supplier.SupplierRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@Slf4j
public class InventoryDataInitializer implements CommandLineRunner {

    private static final String REASON_OPENING_STOCK = "OPENING_STOCK";
    private static final String SEED_ACTOR = "system";

    private final SupplierRepository supplierRepository;
    private final PartRepository partRepository;
    private final StockLedgerService stockLedgerService;

    public InventoryDataInitializer(SupplierRepository supplierRepository,
                                    PartRepository partRepository,
                                    StockLedgerService stockLedgerService) {
        this.supplierRepository = supplierRepository;
        this.partRepository = partRepository;
        this.stockLedgerService = stockLedgerService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedSupplier("Bosch Bulgaria", "orders@bosch.bg", 3);
        seedSupplier("Febi Bilstein", "sales@febi.de", 7);
        seedSupplier("Motul Lubricants", "b2b@motul.bg", 2);

        seedPart("BRK-DISC-320", "Front brake disc 320mm", PartCategory.BRAKES,
                "78.50", 24, 8, 20, "Bosch Bulgaria");
        seedPart("BRK-PAD-SET", "Front brake pad set", PartCategory.BRAKES,
                "52.90", 30, 10, 25, "Bosch Bulgaria");
        seedPart("SUS-ARM-BSH", "Lower control arm bushing", PartCategory.SUSPENSION,
                "24.00", 40, 12, 30, "Febi Bilstein");
        seedPart("SUS-SHOCK-FR", "Front shock absorber", PartCategory.SUSPENSION,
                "134.00", 12, 4, 10, "Febi Bilstein");
        seedPart("FLT-OIL-STD", "Oil filter", PartCategory.FILTERS,
                "14.20", 60, 20, 50, "Bosch Bulgaria");
        seedPart("FLT-AIR-STD", "Air filter", PartCategory.FILTERS,
                "19.80", 45, 15, 40, "Bosch Bulgaria");
        seedPart("ENG-BELT-TMG", "Timing belt kit", PartCategory.ENGINE,
                "189.00", 8, 3, 6, "Febi Bilstein");
        seedPart("ENG-SPARK-4", "Spark plug set of four", PartCategory.ENGINE,
                "46.00", 35, 10, 30, "Bosch Bulgaria");
        seedPart("ELE-BATT-70", "Battery 70Ah", PartCategory.ELECTRICAL,
                "215.00", 10, 4, 8, "Bosch Bulgaria");
        seedPart("ELE-ALT-120", "Alternator 120A", PartCategory.ELECTRICAL,
                "420.00", 5, 2, 4, "Bosch Bulgaria");
        seedPart("FLU-OIL-5W30", "Engine oil 5W-30, 5 litres", PartCategory.FLUIDS,
                "68.00", 50, 15, 40, "Motul Lubricants");
        seedPart("FLU-BRK-DOT4", "Brake fluid DOT4, 1 litre", PartCategory.FLUIDS,
                "16.50", 28, 10, 24, "Motul Lubricants");
    }

    private void seedSupplier(String name, String email, int leadTimeDays) {
        if (supplierRepository.findByNameIgnoreCase(name).isPresent()) {
            return;
        }

        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setEmail(email);
        supplier.setLeadTimeDays(leadTimeDays);
        supplier.setActive(true);

        supplierRepository.save(supplier);
        log.info("Seeded supplier '{}'", name);
    }

    private void seedPart(String sku,
                          String name,
                          PartCategory category,
                          String unitPrice,
                          int quantityOnHand,
                          int reorderLevel,
                          int reorderQuantity,
                          String supplierName) {
        if (partRepository.existsBySkuIgnoreCase(sku)) {
            return;
        }

        Supplier supplier = supplierRepository.findByNameIgnoreCase(supplierName).orElseThrow();

        Part part = new Part();
        part.setSku(sku);
        part.setName(name);
        part.setCategory(category);
        part.setUnitPrice(new BigDecimal(unitPrice));
        part.setQuantityOnHand(quantityOnHand);
        part.setQuantityReserved(0);
        part.setReorderLevel(reorderLevel);
        part.setReorderQuantity(reorderQuantity);
        part.setSupplier(supplier);

        partRepository.save(part);
        stockLedgerService.record(sku, REASON_OPENING_STOCK, SEED_ACTOR, quantityOnHand, 0);

        log.info("Seeded part {} '{}' ({} on hand)", sku, name, quantityOnHand);
    }
}
