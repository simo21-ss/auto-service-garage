package bg.softuni.garage.repairorder;

import bg.softuni.garage.common.event.RepairOrderCancelledEvent;
import bg.softuni.garage.common.event.RepairOrderCompletedEvent;
import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import bg.softuni.garage.mechanic.Mechanic;
import bg.softuni.garage.mechanic.MechanicService;
import bg.softuni.garage.parts.PartsCatalogService;
import bg.softuni.garage.repairorder.dto.AssignmentRequest;
import bg.softuni.garage.repairorder.dto.RepairOrderRequest;
import bg.softuni.garage.user.UserService;
import bg.softuni.garage.vehicle.Vehicle;
import bg.softuni.garage.vehicle.VehicleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class RepairOrderServiceImpl implements RepairOrderService {

    private static final String REFERENCE_FORMAT = "RO-%d-%04d";

    private final RepairOrderRepository repairOrderRepository;
    private final ServiceTaskRepository serviceTaskRepository;
    private final VehicleService vehicleService;
    private final MechanicService mechanicService;
    private final UserService userService;
    private final PartsCatalogService partsCatalogService;
    private final ApplicationEventPublisher eventPublisher;

    public RepairOrderServiceImpl(RepairOrderRepository repairOrderRepository,
                                  ServiceTaskRepository serviceTaskRepository,
                                  VehicleService vehicleService,
                                  MechanicService mechanicService,
                                  UserService userService,
                                  PartsCatalogService partsCatalogService,
                                  ApplicationEventPublisher eventPublisher) {
        this.repairOrderRepository = repairOrderRepository;
        this.serviceTaskRepository = serviceTaskRepository;
        this.vehicleService = vehicleService;
        this.mechanicService = mechanicService;
        this.userService = userService;
        this.partsCatalogService = partsCatalogService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepairOrder> findForCustomer(UUID customerId) {
        return repairOrderRepository.findAllByVehicleOwnerOrderByCreatedAtDesc(
                userService.getById(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepairOrder> findOpenOrders() {
        return repairOrderRepository.findOpenWithOwners(RepairOrderStatus.openStatuses());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepairOrder> findByStatus(RepairOrderStatus status) {
        return repairOrderRepository.findAllByStatusOrderByCreatedAtDesc(status);
    }

    @Override
    @Transactional(readOnly = true)
    public RepairOrder getById(UUID orderId) {
        return repairOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Repair order not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public RepairOrder getForViewer(UUID orderId, UUID viewerId, boolean staffView) {
        RepairOrder order = getById(orderId);
        if (!staffView && !order.getVehicle().getOwner().getId().equals(viewerId)) {
            throw new ResourceNotFoundException("Repair order not found");
        }
        return order;
    }

    @Override
    @Transactional
    public RepairOrder book(RepairOrderRequest request, UUID customerId) {
        Vehicle vehicle = vehicleService.getOwnedById(request.getVehicleId(), customerId);
        if (!vehicle.isActive()) {
            throw new BusinessRuleException(
                    "Vehicle " + vehicle.getPlate() + " is retired and cannot be booked in");
        }
        if (repairOrderRepository.existsByVehicleAndStatusIn(vehicle, RepairOrderStatus.openStatuses())) {
            throw new BusinessRuleException(
                    "Vehicle " + vehicle.getPlate() + " already has an open repair order");
        }

        RepairOrder order = new RepairOrder();
        order.setReference(nextReference());
        order.setVehicle(vehicle);
        order.setComplaint(request.getComplaint().trim());
        order.setRequiredSpecialty(request.getRequiredSpecialty());
        order.setStatus(RepairOrderStatus.REQUESTED);
        order.setCreatedAt(LocalDateTime.now());
        order.setLabourCost(BigDecimal.ZERO);
        order.setPartsCost(BigDecimal.ZERO);

        RepairOrder saved = repairOrderRepository.save(order);
        log.info("Booked repair order {} for vehicle {} ({})",
                saved.getReference(), vehicle.getPlate(), saved.getRequiredSpecialty());
        return saved;
    }

    @Override
    @Transactional
    public RepairOrder assign(UUID orderId, AssignmentRequest request) {
        RepairOrder order = getById(orderId);
        if (order.getStatus() != RepairOrderStatus.REQUESTED
                && order.getStatus() != RepairOrderStatus.SCHEDULED) {
            throw new BusinessRuleException(
                    "Only requested or scheduled orders can be assigned, this one is "
                            + order.getStatus());
        }

        Mechanic mechanic = mechanicService.getById(request.getMechanicId());
        if (!mechanic.isActive()) {
            throw new BusinessRuleException(
                    "'" + mechanic.getFullName() + "' is not currently available");
        }
        if (mechanic.getSpecialty() != order.getRequiredSpecialty()) {
            throw new BusinessRuleException("'" + mechanic.getFullName() + "' specialises in "
                    + mechanic.getSpecialty() + " but this job needs " + order.getRequiredSpecialty());
        }

        order.setMechanic(mechanic);
        order.setScheduledAt(request.getScheduledAt());
        order.setStatus(RepairOrderStatus.SCHEDULED);

        RepairOrder saved = repairOrderRepository.save(order);
        log.info("Assigned repair order {} to '{}' for {}",
                saved.getReference(), mechanic.getFullName(), saved.getScheduledAt());
        return saved;
    }

    @Override
    @Transactional
    public RepairOrder complete(UUID orderId) {
        RepairOrder order = getById(orderId);
        if (order.getStatus() != RepairOrderStatus.IN_PROGRESS) {
            throw new BusinessRuleException(
                    "Only orders in progress can be completed, this one is " + order.getStatus());
        }

        List<ServiceTask> tasks = serviceTaskRepository.findAllByRepairOrderOrderByCreatedAtAsc(order);
        if (tasks.isEmpty()) {
            throw new BusinessRuleException("Add at least one service task before completing the order");
        }
        long pending = serviceTaskRepository.countByRepairOrderAndStatus(order, ServiceTaskStatus.PENDING);
        if (pending > 0) {
            throw new BusinessRuleException(pending + " service task(s) are still pending");
        }

        BigDecimal partsCost = partsCatalogService.consumeAllFor(order.getId());

        order.setStatus(RepairOrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        order.setPartsCost(partsCost);

        RepairOrder saved = repairOrderRepository.save(order);
        eventPublisher.publishEvent(new RepairOrderCompletedEvent(saved.getId(),
                saved.getReference(),
                saved.getVehicle().getPlate(),
                saved.getMechanic() == null ? null : saved.getMechanic().getFullName(),
                saved.getLabourCost().add(saved.getPartsCost())));

        log.info("Completed repair order {} with {} task(s), labour {}, parts {}",
                saved.getReference(), tasks.size(), saved.getLabourCost(), saved.getPartsCost());
        return saved;
    }

    @Override
    @Transactional
    public RepairOrder cancel(UUID orderId, UUID viewerId, boolean staffView) {
        RepairOrder order = getForViewer(orderId, viewerId, staffView);

        if (order.getStatus() == RepairOrderStatus.COMPLETED) {
            throw new BusinessRuleException("A completed repair order cannot be cancelled");
        }
        if (order.getStatus() == RepairOrderStatus.CANCELLED) {
            throw new BusinessRuleException("This repair order is already cancelled");
        }
        if (!staffView && order.getStatus() == RepairOrderStatus.IN_PROGRESS) {
            throw new BusinessRuleException(
                    "Work has already started, please call the workshop to cancel");
        }

        int released = partsCatalogService.releaseAllFor(order.getId());

        order.setStatus(RepairOrderStatus.CANCELLED);

        RepairOrder saved = repairOrderRepository.save(order);
        eventPublisher.publishEvent(new RepairOrderCancelledEvent(saved.getId(),
                saved.getReference(),
                saved.getVehicle().getPlate(),
                released,
                staffView));

        log.info("Cancelled repair order {} ({}), released {} part reservation(s)",
                saved.getReference(), staffView ? "by workshop staff" : "by customer", released);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(RepairOrderStatus status) {
        return repairOrderRepository.countByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpen() {
        return repairOrderRepository.countByStatusIn(RepairOrderStatus.openStatuses());
    }

    private String nextReference() {
        int year = Year.now().getValue();
        String prefix = "RO-" + year + "-";
        long sequence = repairOrderRepository.countByReferencePrefix(prefix) + 1;
        return REFERENCE_FORMAT.formatted(year, sequence);
    }
}
