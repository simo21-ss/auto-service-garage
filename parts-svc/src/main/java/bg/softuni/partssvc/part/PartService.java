package bg.softuni.partssvc.part;

import bg.softuni.partssvc.part.dto.PartResponse;
import bg.softuni.partssvc.part.dto.PartUpsertRequest;
import bg.softuni.partssvc.part.dto.RestockRequest;

import java.util.List;
import java.util.UUID;

public interface PartService {

    List<PartResponse> findAll();

    List<PartResponse> findByCategory(PartCategory category);

    List<PartResponse> findBelowReorderLevel();

    PartResponse getById(UUID id);

    Part getEntityById(UUID id);

    Part getEntityBySku(String sku);

    PartResponse create(PartUpsertRequest request, String actor);

    PartResponse update(UUID id, PartUpsertRequest request, String actor);

    PartResponse restock(UUID id, RestockRequest request, String actor);

    PartResponse toResponse(Part part);
}
