package bg.softuni.garage.common.audit;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/audit")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class AdminAuditController {

    private static final int RECENT_ENTRIES = 100;

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public String auditTrail(Model model) {
        model.addAttribute("entries", auditService.findRecent(RECENT_ENTRIES));
        return "admin/audit";
    }
}
