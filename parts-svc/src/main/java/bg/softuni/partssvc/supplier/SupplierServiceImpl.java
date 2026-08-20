package bg.softuni.partssvc.supplier;

import bg.softuni.partssvc.common.exception.PartNotFoundException;
import bg.softuni.partssvc.supplier.dto.SupplierResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierServiceImpl(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierResponse> findActive() {
        return supplierRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(supplier -> new SupplierResponse(supplier.getId(),
                        supplier.getName(),
                        supplier.getEmail(),
                        supplier.getLeadTimeDays(),
                        supplier.isActive()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Supplier getByName(String name) {
        return supplierRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new PartNotFoundException("Unknown supplier '" + name + "'"));
    }
}
