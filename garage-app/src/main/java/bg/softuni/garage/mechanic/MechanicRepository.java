package bg.softuni.garage.mechanic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MechanicRepository extends JpaRepository<Mechanic, UUID> {

    List<Mechanic> findAllByOrderByFullNameAsc();

    List<Mechanic> findAllByActiveTrueOrderByFullNameAsc();

    boolean existsByFullNameIgnoreCase(String fullName);
}
