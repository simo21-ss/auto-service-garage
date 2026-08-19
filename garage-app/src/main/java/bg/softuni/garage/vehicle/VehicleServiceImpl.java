package bg.softuni.garage.vehicle;

import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.DuplicateResourceException;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import bg.softuni.garage.repairorder.RepairOrderRepository;
import bg.softuni.garage.repairorder.RepairOrderStatus;
import bg.softuni.garage.user.User;
import bg.softuni.garage.user.UserService;
import bg.softuni.garage.vehicle.dto.VehicleRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final RepairOrderRepository repairOrderRepository;
    private final UserService userService;

    public VehicleServiceImpl(VehicleRepository vehicleRepository,
                              RepairOrderRepository repairOrderRepository,
                              UserService userService) {
        this.vehicleRepository = vehicleRepository;
        this.repairOrderRepository = repairOrderRepository;
        this.userService = userService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findOwnedBy(UUID ownerId) {
        return vehicleRepository.findAllByOwnerOrderByRegisteredAtDesc(userService.getById(ownerId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findBookableFor(UUID ownerId) {
        return vehicleRepository.findAllByOwnerAndActiveTrueOrderByPlateAsc(userService.getById(ownerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Vehicle getOwnedById(UUID vehicleId, UUID ownerId) {
        Vehicle vehicle = getById(vehicleId);
        if (!vehicle.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException("Vehicle not found");
        }
        return vehicle;
    }

    @Override
    @Transactional(readOnly = true)
    public Vehicle getById(UUID vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
    }

    @Override
    @Transactional
    public Vehicle register(VehicleRequest request, UUID ownerId) {
        String plate = normalisePlate(request.getPlate());
        if (vehicleRepository.existsByPlateIgnoreCase(plate)) {
            throw new DuplicateResourceException("A vehicle with plate " + plate + " is already registered");
        }

        User owner = userService.getById(ownerId);

        Vehicle vehicle = new Vehicle();
        vehicle.setPlate(plate);
        vehicle.setOwner(owner);
        vehicle.setActive(true);
        vehicle.setRegisteredAt(LocalDateTime.now());
        apply(vehicle, request);

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Registered vehicle {} ({} {}) for '{}'",
                saved.getPlate(), saved.getMake(), saved.getModel(), owner.getUsername());
        return saved;
    }

    @Override
    @Transactional
    public Vehicle update(UUID vehicleId, VehicleRequest request, UUID ownerId) {
        Vehicle vehicle = getOwnedById(vehicleId, ownerId);
        String plate = normalisePlate(request.getPlate());

        if (!vehicle.getPlate().equalsIgnoreCase(plate)
                && vehicleRepository.existsByPlateIgnoreCase(plate)) {
            throw new DuplicateResourceException("A vehicle with plate " + plate + " is already registered");
        }
        if (request.getMileage() < vehicle.getMileage()) {
            throw new BusinessRuleException("Mileage cannot be lower than the previously recorded "
                    + vehicle.getMileage() + " km");
        }

        vehicle.setPlate(plate);
        apply(vehicle, request);

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Updated vehicle {} [{}]", saved.getPlate(), saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public Vehicle setActive(UUID vehicleId, boolean active, UUID ownerId) {
        Vehicle vehicle = getOwnedById(vehicleId, ownerId);
        if (vehicle.isActive() == active) {
            throw new BusinessRuleException("The vehicle is already "
                    + (active ? "in service" : "retired"));
        }
        if (!active && hasOpenOrders(vehicle)) {
            throw new BusinessRuleException(
                    "Vehicle " + vehicle.getPlate() + " has open repair orders and cannot be retired");
        }

        vehicle.setActive(active);

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Vehicle {} marked as {}", saved.getPlate(), active ? "in service" : "retired");
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID vehicleId, UUID ownerId) {
        Vehicle vehicle = getOwnedById(vehicleId, ownerId);
        if (repairOrderRepository.existsByVehicle(vehicle)) {
            throw new BusinessRuleException("Vehicle " + vehicle.getPlate()
                    + " has repair history and can only be retired, not deleted");
        }

        vehicleRepository.delete(vehicle);
        log.info("Deleted vehicle {} [{}]", vehicle.getPlate(), vehicleId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOwnedBy(User owner) {
        return vehicleRepository.countByOwner(owner);
    }

    private boolean hasOpenOrders(Vehicle vehicle) {
        return repairOrderRepository.existsByVehicleAndStatusIn(vehicle, RepairOrderStatus.openStatuses());
    }

    private String normalisePlate(String plate) {
        return plate.trim().toUpperCase().replaceAll("\\s+", " ");
    }

    private void apply(Vehicle vehicle, VehicleRequest request) {
        vehicle.setVin(request.getVin().trim().toUpperCase());
        vehicle.setMake(request.getMake().trim());
        vehicle.setModel(request.getModel().trim());
        vehicle.setModelYear(request.getModelYear());
        vehicle.setMileage(request.getMileage());
    }
}
