package bg.softuni.garage.common.export;

import bg.softuni.garage.parts.dto.PartView;

import java.util.List;

public interface InventoryReportService {

    byte[] renderInventoryWorkbook(List<PartView> parts);
}
