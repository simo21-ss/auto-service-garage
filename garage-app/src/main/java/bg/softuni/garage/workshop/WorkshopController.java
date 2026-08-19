package bg.softuni.garage.workshop;

import bg.softuni.garage.repairorder.RepairOrderService;
import bg.softuni.garage.repairorder.RepairOrderStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/workshop")
public class WorkshopController {

    private final RepairOrderService repairOrderService;

    public WorkshopController(RepairOrderService repairOrderService) {
        this.repairOrderService = repairOrderService;
    }

    @GetMapping
    public String board(Model model) {
        model.addAttribute("openOrders", repairOrderService.findOpenOrders());
        model.addAttribute("completedOrders",
                repairOrderService.findByStatus(RepairOrderStatus.COMPLETED));
        model.addAttribute("requestedCount",
                repairOrderService.countByStatus(RepairOrderStatus.REQUESTED));
        model.addAttribute("scheduledCount",
                repairOrderService.countByStatus(RepairOrderStatus.SCHEDULED));
        model.addAttribute("inProgressCount",
                repairOrderService.countByStatus(RepairOrderStatus.IN_PROGRESS));
        return "workshop/board";
    }
}
