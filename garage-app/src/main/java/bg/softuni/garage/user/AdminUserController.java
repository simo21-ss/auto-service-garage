package bg.softuni.garage.user;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String users(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("roles", RoleName.values());
        return "admin/users";
    }

    @PutMapping("/{id}/role")
    public String changeRole(@PathVariable UUID id,
                             @RequestParam RoleName role,
                             @AuthenticationPrincipal GarageUserDetails principal,
                             RedirectAttributes redirectAttributes) {
        User updated = userService.changeRole(id, role, principal.getId());
        redirectAttributes.addFlashAttribute("success",
                "'" + updated.getUsername() + "' is now a " + role + ".");
        return "redirect:/admin/users";
    }

    @PutMapping("/{id}/status")
    public String changeStatus(@PathVariable UUID id,
                               @RequestParam boolean active,
                               @AuthenticationPrincipal GarageUserDetails principal,
                               RedirectAttributes redirectAttributes) {
        User updated = userService.setActive(id, active, principal.getId());
        redirectAttributes.addFlashAttribute("success",
                "'" + updated.getUsername() + "' has been " + (active ? "activated" : "deactivated") + ".");
        return "redirect:/admin/users";
    }
}
