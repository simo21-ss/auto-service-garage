package bg.softuni.garage.vehicle;

import bg.softuni.garage.user.GarageUserDetails;
import bg.softuni.garage.vehicle.dto.VehicleRequest;
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
@RequestMapping("/vehicles")
@PreAuthorize("hasAuthority('VEHICLE_MANAGE')")
public class VehicleController {

    private static final String REDIRECT_LIST = "redirect:/vehicles";
    private static final String FORM_VIEW = "vehicles/form";

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal GarageUserDetails principal, Model model) {
        model.addAttribute("vehicles", vehicleService.findOwnedBy(principal.getId()));
        return "vehicles/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("vehicleRequest")) {
            model.addAttribute("vehicleRequest", new VehicleRequest());
        }
        model.addAttribute("editing", false);
        return FORM_VIEW;
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("vehicleRequest") VehicleRequest vehicleRequest,
                           BindingResult bindingResult,
                           @AuthenticationPrincipal GarageUserDetails principal,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editing", false);
            return FORM_VIEW;
        }

        Vehicle vehicle = vehicleService.register(vehicleRequest, principal.getId());
        redirectAttributes.addFlashAttribute("success",
                "Vehicle " + vehicle.getPlate() + " has been registered.");
        return REDIRECT_LIST;
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id,
                           @AuthenticationPrincipal GarageUserDetails principal,
                           Model model) {
        if (!model.containsAttribute("vehicleRequest")) {
            model.addAttribute("vehicleRequest",
                    toRequest(vehicleService.getOwnedById(id, principal.getId())));
        }
        model.addAttribute("vehicleId", id);
        model.addAttribute("editing", true);
        return FORM_VIEW;
    }

    @PutMapping("/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("vehicleRequest") VehicleRequest vehicleRequest,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal GarageUserDetails principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("vehicleId", id);
            model.addAttribute("editing", true);
            return FORM_VIEW;
        }

        Vehicle vehicle = vehicleService.update(id, vehicleRequest, principal.getId());
        redirectAttributes.addFlashAttribute("success",
                "Vehicle " + vehicle.getPlate() + " has been updated.");
        return REDIRECT_LIST;
    }

    @PutMapping("/{id}/status")
    public String changeStatus(@PathVariable UUID id,
                               @RequestParam boolean active,
                               @AuthenticationPrincipal GarageUserDetails principal,
                               RedirectAttributes redirectAttributes) {
        Vehicle vehicle = vehicleService.setActive(id, active, principal.getId());
        redirectAttributes.addFlashAttribute("success",
                "Vehicle " + vehicle.getPlate() + " is now "
                        + (active ? "back in service" : "retired") + ".");
        return REDIRECT_LIST;
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable UUID id,
                         @AuthenticationPrincipal GarageUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        vehicleService.delete(id, principal.getId());
        redirectAttributes.addFlashAttribute("success", "The vehicle has been deleted.");
        return REDIRECT_LIST;
    }

    private VehicleRequest toRequest(Vehicle vehicle) {
        VehicleRequest request = new VehicleRequest();
        request.setPlate(vehicle.getPlate());
        request.setVin(vehicle.getVin());
        request.setMake(vehicle.getMake());
        request.setModel(vehicle.getModel());
        request.setModelYear(vehicle.getModelYear());
        request.setMileage(vehicle.getMileage());
        return request;
    }
}
