package bg.softuni.partssvc.part;

import bg.softuni.partssvc.ledger.StockLedgerService;
import bg.softuni.partssvc.ledger.dto.LedgerEntryResponse;
import bg.softuni.partssvc.part.dto.PartResponse;
import bg.softuni.partssvc.part.dto.PartUpsertRequest;
import bg.softuni.partssvc.part.dto.RestockRequest;
import jakarta.validation.Valid;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/parts")
public class PartController {

    private static final int LEDGER_PAGE_SIZE = 25;

    private final PartService partService;
    private final StockLedgerService stockLedgerService;

    public PartController(PartService partService, StockLedgerService stockLedgerService) {
        this.partService = partService;
        this.stockLedgerService = stockLedgerService;
    }

    @GetMapping
    public CollectionModel<EntityModel<PartResponse>> catalogue(
            @RequestParam(required = false) PartCategory category) {
        List<PartResponse> parts = category == null
                ? partService.findAll()
                : partService.findByCategory(category);

        return CollectionModel.of(parts.stream().map(this::withLinks).toList())
                .add(linkTo(methodOn(PartController.class).catalogue(null)).withSelfRel())
                .add(linkTo(methodOn(PartController.class).lowStock()).withRel("low-stock"));
    }

    @GetMapping("/low-stock")
    public CollectionModel<EntityModel<PartResponse>> lowStock() {
        List<EntityModel<PartResponse>> parts = partService.findBelowReorderLevel().stream()
                .map(this::withLinks)
                .toList();

        return CollectionModel.of(parts)
                .add(linkTo(methodOn(PartController.class).lowStock()).withSelfRel())
                .add(linkTo(methodOn(PartController.class).catalogue(null)).withRel("catalogue"));
    }

    @GetMapping("/{id}")
    public EntityModel<PartResponse> byId(@PathVariable UUID id) {
        return withLinks(partService.getById(id));
    }

    @GetMapping("/{id}/ledger")
    public CollectionModel<LedgerEntryResponse> ledger(@PathVariable UUID id) {
        PartResponse part = partService.getById(id);

        return CollectionModel.of(stockLedgerService.findRecentForSku(part.sku(), LEDGER_PAGE_SIZE))
                .add(linkTo(methodOn(PartController.class).ledger(id)).withSelfRel())
                .add(linkTo(methodOn(PartController.class).byId(id)).withRel("part"));
    }

    @PostMapping
    public ResponseEntity<EntityModel<PartResponse>> create(
            @Valid @RequestBody PartUpsertRequest request,
            @AuthenticationPrincipal String actor) {
        EntityModel<PartResponse> created = withLinks(partService.create(request, actor));

        return ResponseEntity
                .created(created.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    public EntityModel<PartResponse> update(@PathVariable UUID id,
                                            @Valid @RequestBody PartUpsertRequest request,
                                            @AuthenticationPrincipal String actor) {
        return withLinks(partService.update(id, request, actor));
    }

    @PostMapping("/{id}/restock")
    public EntityModel<PartResponse> restock(@PathVariable UUID id,
                                             @Valid @RequestBody RestockRequest request,
                                             @AuthenticationPrincipal String actor) {
        return withLinks(partService.restock(id, request, actor));
    }

    private EntityModel<PartResponse> withLinks(PartResponse part) {
        return EntityModel.of(part)
                .add(linkTo(methodOn(PartController.class).byId(part.id())).withSelfRel())
                .add(linkTo(methodOn(PartController.class).catalogue(null)).withRel("catalogue"))
                .add(linkTo(methodOn(PartController.class).ledger(part.id())).withRel("ledger"));
    }
}
