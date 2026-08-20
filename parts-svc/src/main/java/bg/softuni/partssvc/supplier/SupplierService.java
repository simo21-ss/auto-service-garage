package bg.softuni.partssvc.supplier;

import bg.softuni.partssvc.supplier.dto.SupplierResponse;

import java.util.List;

public interface SupplierService {

    List<SupplierResponse> findActive();

    Supplier getByName(String name);
}
