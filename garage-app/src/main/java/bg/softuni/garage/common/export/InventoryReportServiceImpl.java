package bg.softuni.garage.common.export;

import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.parts.dto.PartView;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@Slf4j
public class InventoryReportServiceImpl implements InventoryReportService {

    private static final String[] HEADERS = {
            "SKU", "Part", "Category", "Unit price", "On hand",
            "Reserved", "Available", "Reorder level", "Below reorder", "Supplier"};

    @Override
    public byte[] renderInventoryWorkbook(List<PartView> parts) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Inventory");
            writeHeader(workbook, sheet);
            writeRows(workbook, sheet, parts);

            for (int column = 0; column < HEADERS.length; column++) {
                sheet.autoSizeColumn(column);
            }
            sheet.createFreezePane(0, 1);

            workbook.write(output);
            log.info("Exported an inventory workbook with {} part(s)", parts.size());
            return output.toByteArray();
        } catch (IOException exception) {
            log.error("Could not export the inventory workbook", exception);
            throw new BusinessRuleException("The inventory report could not be generated");
        }
    }

    private void writeHeader(Workbook workbook, Sheet sheet) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);

        Row header = sheet.createRow(0);
        for (int column = 0; column < HEADERS.length; column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(HEADERS[column]);
            cell.setCellStyle(style);
        }
    }

    private void writeRows(Workbook workbook, Sheet sheet, List<PartView> parts) {
        CellStyle money = workbook.createCellStyle();
        money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        CellStyle warning = workbook.createCellStyle();
        Font warningFont = workbook.createFont();
        warningFont.setBold(true);
        warningFont.setColor(IndexedColors.DARK_RED.getIndex());
        warning.setFont(warningFont);

        int rowIndex = 1;
        for (PartView part : parts) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(part.sku());
            row.createCell(1).setCellValue(part.name());
            row.createCell(2).setCellValue(part.category());

            Cell price = row.createCell(3);
            price.setCellValue(part.unitPrice().doubleValue());
            price.setCellStyle(money);

            row.createCell(4).setCellValue(part.quantityOnHand());
            row.createCell(5).setCellValue(part.quantityReserved());

            Cell available = row.createCell(6);
            available.setCellValue(part.quantityAvailable());
            if (part.belowReorderLevel()) {
                available.setCellStyle(warning);
            }

            row.createCell(7).setCellValue(part.reorderLevel());
            row.createCell(8).setCellValue(part.belowReorderLevel() ? "YES" : "NO");
            row.createCell(9).setCellValue(part.supplierName());
        }
    }
}
