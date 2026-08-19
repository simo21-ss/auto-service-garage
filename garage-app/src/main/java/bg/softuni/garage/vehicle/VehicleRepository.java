package bg.softuni.garage.vehicle;

import bg.softuni.garage.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByOwnerOrderByRegisteredAtDesc(User owner);

    List<Vehicle> findAllByOwnerAndActiveTrueOrderByPlateAsc(User owner);

    boolean existsByPlateIgnoreCase(String plate);

    long countByOwner(User owner);
}
