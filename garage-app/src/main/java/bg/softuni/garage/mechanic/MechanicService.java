package bg.softuni.garage.mechanic;

import bg.softuni.garage.mechanic.dto.MechanicRequest;

import java.util.List;
import java.util.UUID;

public interface MechanicService {

    List<Mechanic> findAll();

    List<Mechanic> findActive();

    Mechanic getById(UUID id);

    Mechanic create(MechanicRequest request);

    Mechanic update(UUID id, MechanicRequest request);

    void delete(UUID id);
}
