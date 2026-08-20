package bg.softuni.partssvc.part;

import bg.softuni.partssvc.common.exception.DuplicateSkuException;
import bg.softuni.partssvc.common.exception.PartNotFoundException;
import bg.softuni.partssvc.ledger.StockLedgerService;
import bg.softuni.partssvc.part.dto.PartResponse;
import bg.softuni.partssvc.part.dto.PartUpsertRequest;
import bg.softuni.partssvc.part.dto.RestockRequest;
import bg.softuni.partssvc.supplier.SupplierService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class PartServiceImpl implements PartService {

    private static final String REASON_OPENING_STOCK = "OPENING_STOCK";
    private static final String REASON_RESTOCK = "RESTOCK";

    private final PartRepository partRepository;
    private final SupplierService supplierService;
    private final StockLedgerService stockLedgerService;

    public PartServiceImpl(PartRepository partRepository,
                           SupplierService supplierService,
                           StockLedgerService stockLedgerService) {
        this.partRepository = partRepository;
        this.supplierService = supplierService;
        this.stockLedgerService = stockLedgerService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartResponse> findAll() {
        return partRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartResponse> findByCategory(PartCategory category) {
        return partRepository.findAllByCategoryOrderByNameAsc(category).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartResponse> findBelowReorderLevel() {
        return partRepository.findBelowReorderLevel().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PartResponse getById(UUID id) {
        return toResponse(getEntityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Part getEntityById(UUID id) {
        return partRepository.findById(id)
                .orElseThrow(() -> new PartNotFoundException("No part with id " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Part getEntityBySku(String sku) {
        return partRepository.findBySkuIgnoreCase(sku)
                .orElseThrow(() -> new PartNotFoundException("No part with SKU " + sku));
    }

    @Override
    @Transactional
    public PartResponse create(PartUpsertRequest request, String actor) {
        String sku = request.sku().trim().toUpperCase();
        if (partRepository.existsBySkuIgnoreCase(sku)) {
            throw new DuplicateSkuException("A part with SKU " + sku + " already exists");
        }

        Part part = new Part();
        part.setSku(sku);
        part.setQuantityOnHand(request.quantityOnHand());
        part.setQuantityReserved(0);
        apply(part, request);

        Part saved = partRepository.save(part);
        stockLedgerService.record(saved.getSku(), REASON_OPENING_STOCK, actor,
                saved.getQuantityOnHand(), 0);

        log.info("Created part {} '{}' with opening stock {}",
                saved.getSku(), saved.getName(), saved.getQuantityOnHand());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public PartResponse update(UUID id, PartUpsertRequest request, String actor) {
        Part part = getEntityById(id);
        String sku = request.sku().trim().toUpperCase();

        if (!part.getSku().equalsIgnoreCase(sku) && partRepository.existsBySkuIgnoreCase(sku)) {
            throw new DuplicateSkuException("A part with SKU " + sku + " already exists");
        }

        part.setSku(sku);
        apply(part, request);

        Part saved = partRepository.save(part);
        log.info("Updated part {} by {}", saved.getSku(), actor);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public PartResponse restock(UUID id, RestockRequest request, String actor) {
        Part part = getEntityById(id);
        int before = part.getQuantityOnHand();
        part.setQuantityOnHand(before + request.quantity());

        Part saved = partRepository.save(part);
        stockLedgerService.record(saved.getSku(), REASON_RESTOCK, actor, request.quantity(), before);

        log.info("Restocked {} by {} unit(s), now {} on hand",
                saved.getSku(), request.quantity(), saved.getQuantityOnHand());
        return toResponse(saved);
    }

    @Override
    public PartResponse toResponse(Part part) {
        return new PartResponse(part.getId(),
                part.getSku(),
                part.getName(),
                part.getCategory(),
                part.getUnitPrice(),
                part.getQuantityOnHand(),
                part.getQuantityReserved(),
                part.availableQuantity(),
                part.getReorderLevel(),
                part.availableQuantity() <= part.getReorderLevel(),
                part.getSupplier().getName());
    }

    private void apply(Part part, PartUpsertRequest request) {
        part.setName(request.name().trim());
        part.setCategory(request.category());
        part.setUnitPrice(request.unitPrice());
        part.setReorderLevel(request.reorderLevel());
        part.setReorderQuantity(request.reorderQuantity());
        part.setSupplier(supplierService.getByName(request.supplierName()));
    }
}
