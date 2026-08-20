package bg.softuni.garage.parts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PartCollection(@JsonProperty("_embedded") Embedded embedded) {

    public List<PartView> parts() {
        return embedded == null || embedded.partResponseList() == null
                ? List.of()
                : embedded.partResponseList();
    }

    public record Embedded(@JsonProperty("partResponseList") List<PartView> partResponseList) {
    }

    public static PartCollection empty() {
        return new PartCollection(new Embedded(List.of()));
    }
}
