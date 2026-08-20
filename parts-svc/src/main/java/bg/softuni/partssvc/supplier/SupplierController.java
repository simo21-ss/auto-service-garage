package bg.softuni.partssvc.supplier;

import bg.softuni.partssvc.supplier.dto.SupplierResponse;
import org.springframework.hateoas.CollectionModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public CollectionModel<SupplierResponse> active() {
        return CollectionModel.of(supplierService.findActive())
                .add(linkTo(methodOn(SupplierController.class).active()).withSelfRel());
    }
}
