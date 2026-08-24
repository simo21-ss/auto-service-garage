package bg.softuni.partssvc.part;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartRepository extends JpaRepository<Part, UUID> {

    Optional<Part> findBySkuIgnoreCase(String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Part> findWithLockBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    List<Part> findAllByOrderByNameAsc();

    List<Part> findAllByCategoryOrderByNameAsc(PartCategory category);

    @Query("select p from Part p where p.quantityOnHand - p.quantityReserved <= p.reorderLevel order by p.name asc")
    List<Part> findBelowReorderLevel();
}
