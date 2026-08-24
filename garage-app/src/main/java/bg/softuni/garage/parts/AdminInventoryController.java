package bg.softuni.garage.parts;

import bg.softuni.garage.common.export.InventoryReportService;
import bg.softuni.garage.parts.dto.PartView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/admin/inventory")
@PreAuthorize("hasAuthority('PART_RESTOCK')")
public class AdminInventoryController {

    private static final String REDIRECT_INVENTORY = "redirect:/admin/inventory";

    private final PartsCatalogService partsCatalogService;
    private final InventoryReportService inventoryReportService;

    public AdminInventoryController(PartsCatalogService partsCatalogService,
                                    InventoryReportService inventoryReportService) {
        this.partsCatalogService = partsCatalogService;
        this.inventoryReportService = inventoryReportService;
    }

    @GetMapping
    public String inventory(Model model) {
        model.addAttribute("parts", partsCatalogService.catalogue());
        model.addAttribute("lowStock", partsCatalogService.lowStock());
        return "admin/inventory";
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<byte[]> export() {
        byte[] workbook = inventoryReportService.renderInventoryWorkbook(partsCatalogService.catalogue());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventory.xlsx\"")
                .body(workbook);
    }

    @PostMapping("/{id}/restock")
    public String restock(@PathVariable UUID id,
                          @RequestParam int quantity,
                          @RequestParam(required = false) String note,
                          RedirectAttributes redirectAttributes) {
        PartView part = partsCatalogService.restock(id, quantity, note);
        redirectAttributes.addFlashAttribute("success",
                part.sku() + " restocked, now " + part.quantityOnHand() + " on hand.");
        return REDIRECT_INVENTORY;
    }
}
