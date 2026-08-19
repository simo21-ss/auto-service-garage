package bg.softuni.garage.vehicle;

import bg.softuni.garage.user.User;
import bg.softuni.garage.vehicle.dto.VehicleRequest;

import java.util.List;
import java.util.UUID;

public interface VehicleService {

    List<Vehicle> findOwnedBy(UUID ownerId);

    List<Vehicle> findBookableFor(UUID ownerId);

    Vehicle getOwnedById(UUID vehicleId, UUID ownerId);

    Vehicle getById(UUID vehicleId);

    Vehicle register(VehicleRequest request, UUID ownerId);

    Vehicle update(UUID vehicleId, VehicleRequest request, UUID ownerId);

    Vehicle setActive(UUID vehicleId, boolean active, UUID ownerId);

    void delete(UUID vehicleId, UUID ownerId);

    long countOwnedBy(User owner);
}
