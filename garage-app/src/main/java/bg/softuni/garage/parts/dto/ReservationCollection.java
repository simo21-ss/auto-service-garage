package bg.softuni.garage.parts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ReservationCollection(@JsonProperty("_embedded") Embedded embedded) {

    public List<ReservationView> reservations() {
        return embedded == null || embedded.reservationResponseList() == null
                ? List.of()
                : embedded.reservationResponseList();
    }

    public record Embedded(
            @JsonProperty("reservationResponseList") List<ReservationView> reservationResponseList) {
    }
}
