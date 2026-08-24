package bg.softuni.garage.common.export;

import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.parts.PartsCatalogService;
import bg.softuni.garage.parts.dto.ReservationView;
import bg.softuni.garage.repairorder.RepairOrder;
import bg.softuni.garage.repairorder.RepairOrderService;
import bg.softuni.garage.repairorder.ServiceTask;
import bg.softuni.garage.repairorder.ServiceTaskService;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final Color ACCENT = new Color(217, 119, 6);
    private static final Color LINE = new Color(226, 232, 240);
    private static final String CURRENCY = " BGN";
    private static final String FILE_NAME_FORMAT = "invoice-%s.pdf";

    private final RepairOrderService repairOrderService;
    private final ServiceTaskService serviceTaskService;
    private final PartsCatalogService partsCatalogService;

    public InvoiceServiceImpl(RepairOrderService repairOrderService,
                              ServiceTaskService serviceTaskService,
                              PartsCatalogService partsCatalogService) {
        this.repairOrderService = repairOrderService;
        this.serviceTaskService = serviceTaskService;
        this.partsCatalogService = partsCatalogService;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDocument renderInvoice(UUID orderId, UUID viewerId, boolean staffView) {
        RepairOrder order = repairOrderService.getForViewer(orderId, viewerId, staffView);
        List<ServiceTask> tasks = serviceTaskService.findForOrder(order);
        List<ReservationView> parts = partsCatalogService.reservationsFor(orderId);

        return new InvoiceDocument(FILE_NAME_FORMAT.formatted(order.getReference()),
                render(order, tasks, parts));
    }

    private byte[] render(RepairOrder order, List<ServiceTask> tasks, List<ReservationView> parts) {
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, output);
            document.open();

            writeHeader(document, order);
            writeVehicleBlock(document, order);
            writeTasks(document, tasks);
            writeParts(document, parts);
            writeTotals(document, order);

            document.close();
        } catch (DocumentException exception) {
            log.error("Could not render the invoice for {}", order.getReference(), exception);
            throw new BusinessRuleException("The invoice could not be generated");
        }

        log.info("Rendered invoice for repair order {}", order.getReference());
        return output.toByteArray();
    }

    private void writeHeader(Document document, RepairOrder order) throws DocumentException {
        Paragraph brand = new Paragraph("PISTONWORKS GARAGE",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, ACCENT));
        document.add(brand);

        Paragraph address = new Paragraph("14 Iskarsko Shose, Sofia 1592, Bulgaria",
                FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
        address.setSpacingAfter(18);
        document.add(address);

        Paragraph title = new Paragraph("Invoice " + order.getReference(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
        document.add(title);

        String issued = order.getCompletedAt() == null
                ? "Not completed"
                : order.getCompletedAt().format(STAMP);
        Paragraph issuedOn = new Paragraph("Completed " + issued,
                FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
        issuedOn.setSpacingAfter(16);
        document.add(issuedOn);
    }

    private void writeVehicleBlock(Document document, RepairOrder order) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(18);

        addLabelled(table, "Customer", order.getVehicle().getOwner().getFirstName()
                + " " + order.getVehicle().getOwner().getLastName());
        addLabelled(table, "Vehicle", order.getVehicle().getMake() + " "
                + order.getVehicle().getModel() + " (" + order.getVehicle().getPlate() + ")");
        addLabelled(table, "Mechanic", order.getMechanic() == null
                ? "Not assigned" : order.getMechanic().getFullName());
        addLabelled(table, "Reported problem", order.getComplaint());

        document.add(table);
    }

    private void writeTasks(Document document, List<ServiceTask> tasks) throws DocumentException {
        document.add(sectionTitle("Labour"));

        if (tasks.isEmpty()) {
            document.add(emptyNote("No service tasks were logged."));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{5, 1.4f, 1.6f, 1.8f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(16);

        headerRow(table, "Operation", "Hours", "Rate", "Line total");
        tasks.forEach(task -> {
            bodyCell(table, task.getOperation(), Element.ALIGN_LEFT);
            bodyCell(table, task.getHours().toPlainString(), Element.ALIGN_RIGHT);
            bodyCell(table, task.getHourlyRate().toPlainString(), Element.ALIGN_RIGHT);
            bodyCell(table, task.getHours().multiply(task.getHourlyRate()).toPlainString(),
                    Element.ALIGN_RIGHT);
        });

        document.add(table);
    }

    private void writeParts(Document document, List<ReservationView> parts) throws DocumentException {
        document.add(sectionTitle("Parts"));

        List<ReservationView> fitted = parts.stream()
                .filter(part -> "CONSUMED".equals(part.status()))
                .toList();

        if (fitted.isEmpty()) {
            document.add(emptyNote("No parts were fitted."));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{2, 4, 1.2f, 1.6f, 1.8f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(16);

        headerRow(table, "SKU", "Part", "Qty", "Unit price", "Line total");
        fitted.forEach(part -> {
            bodyCell(table, part.sku(), Element.ALIGN_LEFT);
            bodyCell(table, part.partName(), Element.ALIGN_LEFT);
            bodyCell(table, String.valueOf(part.quantity()), Element.ALIGN_RIGHT);
            bodyCell(table, part.unitPrice().toPlainString(), Element.ALIGN_RIGHT);
            bodyCell(table, part.lineTotal().toPlainString(), Element.ALIGN_RIGHT);
        });

        document.add(table);
    }

    private void writeTotals(Document document, RepairOrder order) throws DocumentException {
        BigDecimal total = order.getLabourCost().add(order.getPartsCost());

        PdfPTable table = new PdfPTable(new float[]{6, 2});
        table.setWidthPercentage(100);

        totalRow(table, "Labour", order.getLabourCost().toPlainString() + CURRENCY, false);
        totalRow(table, "Parts", order.getPartsCost().toPlainString() + CURRENCY, false);
        totalRow(table, "Total due", total.toPlainString() + CURRENCY, true);

        document.add(table);
    }

    private Paragraph sectionTitle(String text) {
        Paragraph title = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
        title.setSpacingAfter(6);
        return title;
    }

    private Paragraph emptyNote(String text) {
        Paragraph note = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
        note.setSpacingAfter(16);
        return note;
    }

    private void addLabelled(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label,
                FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY)));
        labelCell.setBorder(0);
        labelCell.setPaddingBottom(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Paragraph(value,
                FontFactory.getFont(FontFactory.HELVETICA, 10)));
        valueCell.setBorder(0);
        valueCell.setPaddingBottom(5);
        table.addCell(valueCell);
    }

    private void headerRow(PdfPTable table, String... labels) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
        for (String label : labels) {
            PdfPCell cell = new PdfPCell(new Paragraph(label, font));
            cell.setBorder(com.lowagie.text.Rectangle.BOTTOM);
            cell.setBorderColor(LINE);
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private void bodyCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Paragraph(text,
                FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cell.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void totalRow(PdfPTable table, String label, String value, boolean emphasised) {
        Font font = emphasised
                ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)
                : FontFactory.getFont(FontFactory.HELVETICA, 10);

        PdfPCell labelCell = new PdfPCell(new Paragraph(label, font));
        labelCell.setBorder(emphasised ? com.lowagie.text.Rectangle.TOP : 0);
        labelCell.setBorderColor(LINE);
        labelCell.setPadding(6);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Paragraph(value, font));
        valueCell.setBorder(emphasised ? com.lowagie.text.Rectangle.TOP : 0);
        valueCell.setBorderColor(LINE);
        valueCell.setPadding(6);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }
}
