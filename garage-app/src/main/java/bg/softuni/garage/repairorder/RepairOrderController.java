package bg.softuni.garage.repairorder;

import bg.softuni.garage.mechanic.MechanicService;
import bg.softuni.garage.mechanic.Specialty;
import bg.softuni.garage.parts.PartsCatalogService;
import bg.softuni.garage.repairorder.dto.AssignmentRequest;
import bg.softuni.garage.repairorder.dto.RepairOrderRequest;
import bg.softuni.garage.repairorder.dto.ServiceTaskRequest;
import bg.softuni.garage.user.GarageUserDetails;
import bg.softuni.garage.vehicle.VehicleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/orders")
public class RepairOrderController {

    private static final String REDIRECT_LIST = "redirect:/orders";
    private static final String BOOKING_VIEW = "orders/form";

    private final RepairOrderService repairOrderService;
    private final ServiceTaskService serviceTaskService;
    private final VehicleService vehicleService;
    private final MechanicService mechanicService;
    private final PartsCatalogService partsCatalogService;

    public RepairOrderController(RepairOrderService repairOrderService,
                                 ServiceTaskService serviceTaskService,
                                 VehicleService vehicleService,
                                 MechanicService mechanicService,
                                 PartsCatalogService partsCatalogService) {
        this.repairOrderService = repairOrderService;
        this.serviceTaskService = serviceTaskService;
        this.vehicleService = vehicleService;
        this.mechanicService = mechanicService;
        this.partsCatalogService = partsCatalogService;
    }

    @GetMapping
    public String myOrders(@AuthenticationPrincipal GarageUserDetails principal, Model model) {
        model.addAttribute("orders", repairOrderService.findForCustomer(principal.getId()));
        return "orders/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('ORDER_BOOK')")
    public String bookingForm(@AuthenticationPrincipal GarageUserDetails principal, Model model) {
        if (!model.containsAttribute("repairOrderRequest")) {
            model.addAttribute("repairOrderRequest", new RepairOrderRequest());
        }
        model.addAttribute("vehicles", vehicleService.findBookableFor(principal.getId()));
        model.addAttribute("specialties", Specialty.values());
        return BOOKING_VIEW;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORDER_BOOK')")
    public String book(@Valid @ModelAttribute("repairOrderRequest") RepairOrderRequest repairOrderRequest,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal GarageUserDetails principal,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("vehicles", vehicleService.findBookableFor(principal.getId()));
            model.addAttribute("specialties", Specialty.values());
            return BOOKING_VIEW;
        }

        RepairOrder order = repairOrderService.book(repairOrderRequest, principal.getId());
        redirectAttributes.addFlashAttribute("success",
                "Repair order " + order.getReference() + " has been booked.");
        return REDIRECT_LIST;
    }

    @GetMapping("/{id}")
    public String details(@PathVariable UUID id,
                          @AuthenticationPrincipal GarageUserDetails principal,
                          Model model) {
        boolean staffView = isStaff(principal);
        RepairOrder order = repairOrderService.getForViewer(id, principal.getId(), staffView);

        model.addAttribute("order", order);
        model.addAttribute("tasks", serviceTaskService.findForOrder(order));
        model.addAttribute("staffView", staffView);
        model.addAttribute("reservations", partsCatalogService.reservationsFor(order.getId()));
        if (staffView) {
            model.addAttribute("mechanics", mechanicService.findActive());
            model.addAttribute("assignmentRequest", new AssignmentRequest());
            model.addAttribute("serviceTaskRequest", new ServiceTaskRequest());
            model.addAttribute("catalogue", partsCatalogService.catalogue());
        }
        return "orders/details";
    }

    @PutMapping("/{id}/cancel")
    public String cancel(@PathVariable UUID id,
                         @AuthenticationPrincipal GarageUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        RepairOrder order = repairOrderService.cancel(id, principal.getId(), isStaff(principal));
        redirectAttributes.addFlashAttribute("success",
                "Repair order " + order.getReference() + " has been cancelled.");
        return REDIRECT_LIST;
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('ORDER_ASSIGN')")
    public String assign(@PathVariable UUID id,
                         @Valid @ModelAttribute("assignmentRequest") AssignmentRequest assignmentRequest,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", firstError(bindingResult));
            return redirectToDetails(id);
        }

        RepairOrder order = repairOrderService.assign(id, assignmentRequest);
        redirectAttributes.addFlashAttribute("success",
                "Assigned to " + order.getMechanic().getFullName() + ".");
        return redirectToDetails(id);
    }

    @PostMapping("/{id}/tasks")
    @PreAuthorize("hasAuthority('ORDER_WORK')")
    public String addTask(@PathVariable UUID id,
                          @Valid @ModelAttribute("serviceTaskRequest") ServiceTaskRequest serviceTaskRequest,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", firstError(bindingResult));
            return redirectToDetails(id);
        }

        serviceTaskService.add(id, serviceTaskRequest);
        redirectAttributes.addFlashAttribute("success", "Service task added.");
        return redirectToDetails(id);
    }

    @PutMapping("/{id}/tasks/{taskId}/complete")
    @PreAuthorize("hasAuthority('ORDER_WORK')")
    public String completeTask(@PathVariable UUID id,
                               @PathVariable UUID taskId,
                               RedirectAttributes redirectAttributes) {
        serviceTaskService.complete(id, taskId);
        redirectAttributes.addFlashAttribute("success", "Task marked as done.");
        return redirectToDetails(id);
    }

    @DeleteMapping("/{id}/tasks/{taskId}")
    @PreAuthorize("hasAuthority('ORDER_WORK')")
    public String removeTask(@PathVariable UUID id,
                             @PathVariable UUID taskId,
                             RedirectAttributes redirectAttributes) {
        serviceTaskService.remove(id, taskId);
        redirectAttributes.addFlashAttribute("success", "Task removed.");
        return redirectToDetails(id);
    }

    @PostMapping("/{id}/parts")
    @PreAuthorize("hasAuthority('PART_RESERVE')")
    public String reservePart(@PathVariable UUID id,
                              @RequestParam String sku,
                              @RequestParam int quantity,
                              RedirectAttributes redirectAttributes) {
        partsCatalogService.reserve(id, sku, quantity);
        redirectAttributes.addFlashAttribute("success", quantity + " x " + sku + " reserved.");
        return redirectToDetails(id);
    }

    @DeleteMapping("/{id}/parts/{reservationId}")
    @PreAuthorize("hasAuthority('PART_RESERVE')")
    public String releasePart(@PathVariable UUID id,
                              @PathVariable UUID reservationId,
                              RedirectAttributes redirectAttributes) {
        partsCatalogService.release(reservationId);
        redirectAttributes.addFlashAttribute("success", "The part has been returned to stock.");
        return redirectToDetails(id);
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('ORDER_WORK')")
    public String completeOrder(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        RepairOrder order = repairOrderService.complete(id);
        redirectAttributes.addFlashAttribute("success",
                "Repair order " + order.getReference() + " is complete.");
        return redirectToDetails(id);
    }

    private boolean isStaff(GarageUserDetails principal) {
        return principal.getAuthorities().stream()
                .anyMatch(authority -> "ORDER_WORK".equals(authority.getAuthority())
                        || "ORDER_ASSIGN".equals(authority.getAuthority()));
    }

    private String redirectToDetails(UUID id) {
        return "redirect:/orders/" + id;
    }

    private String firstError(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("The submitted values are not valid.");
    }
}
