package bg.softuni.garage.mechanic;

import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.DuplicateResourceException;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import bg.softuni.garage.mechanic.dto.MechanicRequest;
import bg.softuni.garage.repairorder.RepairOrderRepository;
import bg.softuni.garage.repairorder.RepairOrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class MechanicServiceImpl implements MechanicService {

    private final MechanicRepository mechanicRepository;
    private final RepairOrderRepository repairOrderRepository;

    public MechanicServiceImpl(MechanicRepository mechanicRepository,
                               RepairOrderRepository repairOrderRepository) {
        this.mechanicRepository = mechanicRepository;
        this.repairOrderRepository = repairOrderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Mechanic> findAll() {
        return mechanicRepository.findAllByOrderByFullNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Mechanic> findActive() {
        return mechanicRepository.findAllByActiveTrueOrderByFullNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Mechanic getById(UUID id) {
        return mechanicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mechanic not found"));
    }

    @Override
    @Transactional
    public Mechanic create(MechanicRequest request) {
        String fullName = request.getFullName().trim();
        if (mechanicRepository.existsByFullNameIgnoreCase(fullName)) {
            throw new DuplicateResourceException("A mechanic named '" + fullName + "' already exists");
        }

        Mechanic mechanic = new Mechanic();
        mechanic.setFullName(fullName);
        apply(mechanic, request);

        Mechanic saved = mechanicRepository.save(mechanic);
        log.info("Added mechanic '{}' ({}) at {} per hour",
                saved.getFullName(), saved.getSpecialty(), saved.getHourlyRate());
        return saved;
    }

    @Override
    @Transactional
    public Mechanic update(UUID id, MechanicRequest request) {
        Mechanic mechanic = getById(id);
        String fullName = request.getFullName().trim();

        if (!mechanic.getFullName().equalsIgnoreCase(fullName)
                && mechanicRepository.existsByFullNameIgnoreCase(fullName)) {
            throw new DuplicateResourceException("A mechanic named '" + fullName + "' already exists");
        }
        if (!request.isActive() && hasOpenWork(mechanic)) {
            throw new BusinessRuleException(
                    "'" + mechanic.getFullName() + "' still has open repair orders and cannot be deactivated");
        }

        mechanic.setFullName(fullName);
        apply(mechanic, request);

        Mechanic saved = mechanicRepository.save(mechanic);
        log.info("Updated mechanic '{}' [{}]", saved.getFullName(), saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Mechanic mechanic = getById(id);
        if (hasOpenWork(mechanic)) {
            throw new BusinessRuleException(
                    "'" + mechanic.getFullName() + "' is assigned to open repair orders and cannot be removed");
        }
        if (repairOrderRepository.existsByMechanic(mechanic)) {
            throw new BusinessRuleException("'" + mechanic.getFullName()
                    + "' appears on completed repair orders and can only be deactivated, not removed");
        }

        mechanicRepository.delete(mechanic);
        log.info("Removed mechanic '{}' [{}]", mechanic.getFullName(), id);
    }

    private boolean hasOpenWork(Mechanic mechanic) {
        return repairOrderRepository.existsByMechanicAndStatusIn(mechanic, RepairOrderStatus.openStatuses());
    }

    private void apply(Mechanic mechanic, MechanicRequest request) {
        mechanic.setSpecialty(request.getSpecialty());
        mechanic.setHourlyRate(request.getHourlyRate());
        mechanic.setHiredOn(request.getHiredOn());
        mechanic.setActive(request.isActive());
    }
}
