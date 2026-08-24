package bg.softuni.garage.export;

import bg.softuni.garage.common.export.InventoryReportServiceImpl;
import bg.softuni.garage.parts.dto.PartView;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReportServiceTest {

    private final InventoryReportServiceImpl inventoryReportService = new InventoryReportServiceImpl();

    @Test
    void theInventoryWorkbookCarriesAHeaderAndOneRowPerPart() throws IOException {
        byte[] xlsx = inventoryReportService.renderInventoryWorkbook(
                List.of(part("BRK-1", 20, false), part("ELE-2", 1, true)));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheet("Inventory");

            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("SKU");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);

            Row first = sheet.getRow(1);
            assertThat(first.getCell(0).getStringCellValue()).isEqualTo("BRK-1");
            assertThat(first.getCell(6).getNumericCellValue()).isEqualTo(20);
            assertThat(first.getCell(8).getStringCellValue()).isEqualTo("NO");

            assertThat(sheet.getRow(2).getCell(8).getStringCellValue()).isEqualTo("YES");
        }
    }

    @Test
    void anEmptyInventoryStillProducesAValidWorkbook() throws IOException {
        byte[] xlsx = inventoryReportService.renderInventoryWorkbook(List.of());

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getSheet("Inventory").getLastRowNum()).isZero();
        }
    }

    private PartView part(String sku, int available, boolean belowReorder) {
        return new PartView(UUID.randomUUID(), sku, "Part " + sku, "BRAKES",
                new BigDecimal("78.50"), available, 0, available, 5, belowReorder, "Bosch Bulgaria");
    }
}
