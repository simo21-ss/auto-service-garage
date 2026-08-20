package bg.softuni.garage.parts;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/parts")
public class PartsController {

    private final PartsCatalogService partsCatalogService;

    public PartsController(PartsCatalogService partsCatalogService) {
        this.partsCatalogService = partsCatalogService;
    }

    @GetMapping
    public String catalogue(Model model) {
        model.addAttribute("parts", partsCatalogService.catalogue());
        return "parts/catalogue";
    }
}
