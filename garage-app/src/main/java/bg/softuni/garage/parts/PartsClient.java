package bg.softuni.garage.parts;

import bg.softuni.garage.parts.dto.PartCollection;
import bg.softuni.garage.parts.dto.PartView;
import bg.softuni.garage.parts.dto.ReservationCollection;
import bg.softuni.garage.parts.dto.ReservationCommand;
import bg.softuni.garage.parts.dto.ReservationView;
import bg.softuni.garage.parts.dto.RestockCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "parts-svc", url = "${garage.parts-service.url}",
        configuration = PartsClientConfig.class)
public interface PartsClient {

    @GetMapping("/api/parts")
    PartCollection catalogue();

    @GetMapping("/api/parts/low-stock")
    PartCollection lowStock();

    @GetMapping("/api/reservations")
    ReservationCollection reservationsFor(@RequestParam("repairOrderId") UUID repairOrderId);

    @PostMapping("/api/reservations")
    ReservationView reserve(@RequestBody ReservationCommand command);

    @PutMapping("/api/reservations/{id}/consume")
    ReservationView consume(@PathVariable("id") UUID reservationId);

    @DeleteMapping("/api/reservations/{id}")
    ReservationView release(@PathVariable("id") UUID reservationId);

    @PostMapping("/api/parts/{id}/restock")
    PartView restock(@PathVariable("id") UUID partId, @RequestBody RestockCommand command);
}
