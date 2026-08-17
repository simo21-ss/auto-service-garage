package bg.softuni.partssvc.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByNameIgnoreCase(String name);

    List<Supplier> findAllByActiveTrueOrderByNameAsc();
}
