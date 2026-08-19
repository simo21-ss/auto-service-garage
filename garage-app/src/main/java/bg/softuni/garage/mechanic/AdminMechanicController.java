package bg.softuni.garage.mechanic;

import bg.softuni.garage.mechanic.dto.MechanicRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/admin/mechanics")
@PreAuthorize("hasAuthority('MECHANIC_MANAGE')")
public class AdminMechanicController {

    private static final String REDIRECT_LIST = "redirect:/admin/mechanics";

    private final MechanicService mechanicService;

    public AdminMechanicController(MechanicService mechanicService) {
        this.mechanicService = mechanicService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("mechanics", mechanicService.findAll());
        return "admin/mechanics";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("mechanicRequest")) {
            model.addAttribute("mechanicRequest", new MechanicRequest());
        }
        model.addAttribute("specialties", Specialty.values());
        model.addAttribute("editing", false);
        return "admin/mechanic-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("mechanicRequest") MechanicRequest mechanicRequest,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("specialties", Specialty.values());
            model.addAttribute("editing", false);
            return "admin/mechanic-form";
        }

        Mechanic created = mechanicService.create(mechanicRequest);
        redirectAttributes.addFlashAttribute("success",
                "'" + created.getFullName() + "' has joined the workshop.");
        return REDIRECT_LIST;
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        if (!model.containsAttribute("mechanicRequest")) {
            model.addAttribute("mechanicRequest", toRequest(mechanicService.getById(id)));
        }
        model.addAttribute("specialties", Specialty.values());
        model.addAttribute("mechanicId", id);
        model.addAttribute("editing", true);
        return "admin/mechanic-form";
    }

    @PutMapping("/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("mechanicRequest") MechanicRequest mechanicRequest,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("specialties", Specialty.values());
            model.addAttribute("mechanicId", id);
            model.addAttribute("editing", true);
            return "admin/mechanic-form";
        }

        Mechanic updated = mechanicService.update(id, mechanicRequest);
        redirectAttributes.addFlashAttribute("success",
                "'" + updated.getFullName() + "' has been updated.");
        return REDIRECT_LIST;
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        mechanicService.delete(id);
        redirectAttributes.addFlashAttribute("success", "The mechanic has been removed.");
        return REDIRECT_LIST;
    }

    private MechanicRequest toRequest(Mechanic mechanic) {
        MechanicRequest request = new MechanicRequest();
        request.setFullName(mechanic.getFullName());
        request.setSpecialty(mechanic.getSpecialty());
        request.setHourlyRate(mechanic.getHourlyRate());
        request.setHiredOn(mechanic.getHiredOn());
        request.setActive(mechanic.isActive());
        return request;
    }
}
