package com.shoptopus.core.invoice;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvoicePdfService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font MUTED_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);

    public byte[] renderReceiptPdf(Invoice invoice) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, output);
            document.open();

            addHeader(document, invoice);
            addOrderMeta(document, invoice);
            addItems(document, invoice);
            addTotals(document, invoice);

            document.close();
            return output.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to render invoice PDF", ex);
        }
    }

    private void addHeader(Document document, Invoice invoice) throws DocumentException {
        Paragraph title = new Paragraph("Receipt", TITLE_FONT);
        title.setSpacingAfter(8f);
        document.add(title);

        String slug = invoice.getSlug() == null ? invoice.getId().toString() : invoice.getSlug();
        Paragraph invoiceId = new Paragraph("Invoice ID: " + slug, BODY_FONT);
        invoiceId.setSpacingAfter(4f);
        document.add(invoiceId);

        Map<String, Object> payment = safeMap(invoice.getPayment());
        Paragraph paymentMeta = new Paragraph(
                "Payment ID: " + string(payment.get("payment_id"), "—")
                        + "    Payment method: " + string(payment.get("provider"), "—"),
                BODY_FONT
        );
        paymentMeta.setSpacingAfter(16f);
        document.add(paymentMeta);
    }

    private void addOrderMeta(Document document, Invoice invoice) throws DocumentException {
        Map<String, Object> address = safeMap(invoice.getAddress());
        Map<String, Object> delivery = safeMap(invoice.getDeliveryType());
        String orderId = invoice.getOrder() == null ? "—" : invoice.getOrder().getId().toString();

        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100f);
        metaTable.setWidths(new float[]{1f, 1f});
        metaTable.setSpacingAfter(16f);

        metaTable.addCell(boxedCell("Order ID", orderId));
        metaTable.addCell(boxedCell("Delivery", deliverySummary(delivery)));
        metaTable.addCell(boxedCell("Ship to", addressSummary(address)));
        metaTable.addCell(boxedCell("Created", invoice.getCreatedAt() == null ? "—" : invoice.getCreatedAt().toString()));

        document.add(metaTable);
    }

    private void addItems(Document document, Invoice invoice) throws DocumentException {
        Paragraph section = new Paragraph("Products", SECTION_FONT);
        section.setSpacingAfter(8f);
        document.add(section);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) safeMap(invoice.getProducts()).getOrDefault("items", List.of());
        for (Map<String, Object> item : items) {
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100f);
            table.setWidths(new float[]{70f, 430f});
            table.setSpacingAfter(12f);

            table.addCell(imageCell(item));
            table.addCell(itemDetailCell(item));
            document.add(table);
        }
    }

    private void addTotals(Document document, Invoice invoice) throws DocumentException {
        Paragraph section = new Paragraph("Totals", SECTION_FONT);
        section.setSpacingBefore(4f);
        section.setSpacingAfter(8f);
        document.add(section);

        Map<String, Object> totals = safeMap(invoice.getTotals());
        Map<String, Object> voucher = safeMap(invoice.getVoucherCode());

        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Items subtotal", money(totals.get("subtotal_amount")));
        rows.put("Total saved", money(totals.get("discount_amount")));
        rows.put("Delivery", money(totals.get("delivery_amount")));
        if (!voucher.isEmpty()) {
            rows.put("Voucher discount", money(voucher.get("discount_amount")));
        }
        rows.put("Total", money(totals.get("total_amount")));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setWidths(new float[]{3f, 1f});
        for (Map.Entry<String, String> row : rows.entrySet()) {
            table.addCell(plainCell(row.getKey(), Element.ALIGN_LEFT, true));
            table.addCell(plainCell(row.getValue(), Element.ALIGN_RIGHT, true));
        }
        document.add(table);
    }

    private PdfPCell imageCell(Map<String, Object> item) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingRight(10f);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        try {
            String base64 = string(item.get("cover_thumbnail_base64"), null);
            if (base64 != null && !base64.isBlank()) {
                byte[] imageBytes = Base64.getDecoder().decode(base64);
                Image image = Image.getInstance(imageBytes);
                image.scaleAbsolute(52f, 52f);
                image.setAlignment(Element.ALIGN_CENTER);
                cell.addElement(image);
                return cell;
            }
        } catch (Exception ignored) {
        }
        cell.addElement(new Paragraph("No image", MUTED_FONT));
        return cell;
    }

    private PdfPCell itemDetailCell(Map<String, Object> item) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(10f);

        cell.addElement(new Paragraph(string(item.get("name"), "Product"), BODY_FONT));

        Map<String, Object> variant = safeMap(item.get("variant"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) variant.getOrDefault("attributes", List.of());
        if (!attributes.isEmpty()) {
            String attributeSummary = attributes.stream()
                    .map(attribute -> string(attribute.get("attribute_name"), "") + ": " + string(attribute.get("option_name"), ""))
                    .reduce((left, right) -> left + " · " + right)
                    .orElse("");
            cell.addElement(new Paragraph(attributeSummary, MUTED_FONT));
        }
        String customizationValue = string(item.get("customization_value"), null);
        if (customizationValue != null && !customizationValue.isBlank()) {
            cell.addElement(new Paragraph("Customization: " + customizationValue, MUTED_FONT));
        }

        PdfPTable pricing = new PdfPTable(2);
        pricing.setWidthPercentage(100f);
        pricing.setWidths(new float[]{3f, 1f});
        pricing.addCell(plainCell(
                "Qty " + string(item.get("quantity"), "0")
                        + " · Unit " + money(item.get("unit_price"))
                        + " · Original " + money(item.get("original_unit_price"))
                        + " · Discounted " + money(item.get("unit_price")),
                Element.ALIGN_LEFT,
                false
        ));
        pricing.addCell(plainCell(money(item.get("line_total_amount")), Element.ALIGN_RIGHT, false));
        cell.addElement(pricing);
        return cell;
    }

    private PdfPCell boxedCell(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10f);
        cell.setBorderWidth(0.5f);
        cell.setBorderColor(new java.awt.Color(210, 210, 210));
        cell.addElement(new Paragraph(label, MUTED_FONT));
        cell.addElement(new Paragraph(value == null || value.isBlank() ? "—" : value, BODY_FONT));
        return cell;
    }

    private PdfPCell plainCell(String value, int horizontalAlignment, boolean strong) {
        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "—" : value, strong ? BODY_FONT : MUTED_FONT));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(horizontalAlignment);
        cell.setPaddingTop(4f);
        cell.setPaddingBottom(4f);
        return cell;
    }

    private String addressSummary(Map<String, Object> address) {
        return Arrays.stream(new String[]{
                string(address.get("name"), null),
                string(address.get("address_line_1"), null),
                string(address.get("address_line_2"), null),
                joinTown(address)
        }).filter(value -> value != null && !value.isBlank()).reduce((left, right) -> left + "\n" + right).orElse("—");
    }

    private String joinTown(Map<String, Object> address) {
        return Arrays.stream(new String[]{
                string(address.get("town"), null),
                string(address.get("post_code"), null),
                string(address.get("country"), null)
        }).filter(value -> value != null && !value.isBlank()).reduce((left, right) -> left + ", " + right).orElse(null);
    }

    private String deliverySummary(Map<String, Object> delivery) {
        String name = string(delivery.get("name"), null);
        String price = money(delivery.get("price"));
        if (name == null || name.isBlank()) {
            return price;
        }
        return name + "\n" + price;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String string(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Map<?, ?> map) {
            Object english = map.get("en");
            return english == null ? fallback : String.valueOf(english);
        }
        return String.valueOf(value);
    }

    private String money(Object value) {
        if (value == null) {
            return "GBP 0.00";
        }
        BigDecimal amount;
        if (value instanceof BigDecimal decimal) {
            amount = decimal;
        } else {
            try {
                amount = new BigDecimal(String.valueOf(value));
            } catch (NumberFormatException ex) {
                return String.valueOf(value);
            }
        }
        return "GBP " + amount.setScale(2, RoundingMode.HALF_UP);
    }
}
