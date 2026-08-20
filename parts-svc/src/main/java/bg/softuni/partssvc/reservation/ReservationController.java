package bg.softuni.partssvc.reservation;

import bg.softuni.partssvc.part.PartController;
import bg.softuni.partssvc.part.PartService;
import bg.softuni.partssvc.reservation.dto.ReservationRequest;
import bg.softuni.partssvc.reservation.dto.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final PartService partService;

    public ReservationController(ReservationService reservationService, PartService partService) {
        this.reservationService = reservationService;
        this.partService = partService;
    }

    @GetMapping
    public CollectionModel<EntityModel<ReservationResponse>> forRepairOrder(
            @RequestParam UUID repairOrderId) {
        List<EntityModel<ReservationResponse>> reservations =
                reservationService.findForRepairOrder(repairOrderId).stream()
                        .map(this::withLinks)
                        .toList();

        return CollectionModel.of(reservations)
                .add(linkTo(methodOn(ReservationController.class)
                        .forRepairOrder(repairOrderId)).withSelfRel());
    }

    @GetMapping("/{id}")
    public EntityModel<ReservationResponse> byId(@PathVariable UUID id) {
        return withLinks(reservationService.getById(id));
    }

    @PostMapping
    public ResponseEntity<EntityModel<ReservationResponse>> reserve(
            @Valid @RequestBody ReservationRequest request,
            @AuthenticationPrincipal String actor) {
        EntityModel<ReservationResponse> created =
                withLinks(reservationService.reserve(request, actor));

        return ResponseEntity
                .created(created.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(created);
    }

    @PutMapping("/{id}/consume")
    public EntityModel<ReservationResponse> consume(@PathVariable UUID id,
                                                    @AuthenticationPrincipal String actor) {
        return withLinks(reservationService.consume(id, actor));
    }

    @DeleteMapping("/{id}")
    public EntityModel<ReservationResponse> release(@PathVariable UUID id,
                                                    @AuthenticationPrincipal String actor) {
        return withLinks(reservationService.release(id, actor));
    }

    private EntityModel<ReservationResponse> withLinks(ReservationResponse reservation) {
        EntityModel<ReservationResponse> model = EntityModel.of(reservation)
                .add(linkTo(methodOn(ReservationController.class).byId(reservation.id())).withSelfRel())
                .add(linkTo(methodOn(ReservationController.class)
                        .forRepairOrder(reservation.repairOrderId())).withRel("repair-order-reservations"))
                .add(linkTo(methodOn(PartController.class)
                        .byId(partService.getEntityBySku(reservation.sku()).getId())).withRel("part"));

        if (reservation.status() == ReservationStatus.RESERVED) {
            model.add(linkTo(methodOn(ReservationController.class)
                    .consume(reservation.id(), null)).withRel("consume"));
            model.add(linkTo(methodOn(ReservationController.class)
                    .release(reservation.id(), null)).withRel("release"));
        }
        return model;
    }
}
