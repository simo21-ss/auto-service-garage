package bg.softuni.garage.user;

import bg.softuni.garage.common.exception.DuplicateResourceException;
import bg.softuni.garage.user.dto.ProfileRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String profile(@AuthenticationPrincipal GarageUserDetails principal, Model model) {
        model.addAttribute("user", userService.getById(principal.getId()));
        return "profile/view";
    }

    @GetMapping("/edit")
    public String editForm(@AuthenticationPrincipal GarageUserDetails principal, Model model) {
        if (!model.containsAttribute("profileRequest")) {
            model.addAttribute("profileRequest", toRequest(userService.getById(principal.getId())));
        }
        return "profile/edit";
    }

    @PutMapping
    public String update(@AuthenticationPrincipal GarageUserDetails principal,
                         @Valid @ModelAttribute("profileRequest") ProfileRequest profileRequest,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "profile/edit";
        }

        try {
            userService.updateProfile(principal.getId(), profileRequest);
        } catch (DuplicateResourceException exception) {
            bindingResult.rejectValue("email", "email.taken", exception.getMessage());
            return "profile/edit";
        }

        redirectAttributes.addFlashAttribute("success", "Your profile has been updated.");
        return "redirect:/profile";
    }

    private ProfileRequest toRequest(User user) {
        ProfileRequest request = new ProfileRequest();
        request.setEmail(user.getEmail());
        request.setFirstName(user.getFirstName());
        request.setLastName(user.getLastName());
        request.setPhone(user.getPhone());
        return request;
    }
}
