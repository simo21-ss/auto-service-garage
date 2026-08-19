package bg.softuni.garage.home;

import bg.softuni.garage.mechanic.MechanicService;
import bg.softuni.garage.repairorder.RepairOrderService;
import bg.softuni.garage.repairorder.RepairOrderStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HomeController {

    private final RepairOrderService repairOrderService;
    private final MechanicService mechanicService;

    public HomeController(RepairOrderService repairOrderService, MechanicService mechanicService) {
        this.repairOrderService = repairOrderService;
        this.mechanicService = mechanicService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("openOrders", repairOrderService.countOpen());
        model.addAttribute("completedOrders",
                repairOrderService.countByStatus(RepairOrderStatus.COMPLETED));
        model.addAttribute("availableMechanics", mechanicService.findActive().size());
        return "index";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @RequestMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}
