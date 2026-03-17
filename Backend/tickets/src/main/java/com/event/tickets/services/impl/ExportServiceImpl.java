package com.event.tickets.services.impl;

import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.ReportGenerationException;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuditLogService;
import com.event.tickets.services.EventService;
import com.event.tickets.services.ExportService;
import com.event.tickets.services.SystemUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;

/**
 * C-06 FIX: sanitizeForFilename SIOOBE eliminated.
 * The regex calls (.replaceAll) can SHORTEN the string, but .substring()
 * was still using input.length() (the ORIGINAL length) as the upper bound.
 * If input was "Summer Fest!!!" (15 chars) and the regex stripped it to
 * "summer fest" (11 chars), substring(0, min(15, 50)) would attempt to
 * read indices 11–14 on an 11-char string → StringIndexOutOfBoundsException.
 * Fix: capture the SANITIZED result first, then apply min(sanitized.length(), 50).
 *
 * H-12 FIX: Excel cells now receive BigDecimal values directly via
 * setCellValue(double) using bigDecimal.doubleValue() ONLY for the POI call —
 * but the underlying dashboard data stays BigDecimal throughout. POI's
 * setCellValue() requires a Java double; the conversion is unavoidable at the
 * rendering boundary. All arithmetic above this layer remains BigDecimal-safe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportServiceImpl implements ExportService {

    private final EventService eventService;
    private final UserRepository userRepository;
    private final SystemUserProvider systemUserProvider;
    private final AuditLogService auditLogService;

    private static final DateTimeFormatter FILENAME_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public byte[] generateSalesReportExcel(UUID organizerId, UUID eventId) {
        log.info("Generating sales report Excel: organizerId={}, eventId={}", organizerId, eventId);
        Map<String, Object> salesData = eventService.getSalesDashboard(organizerId, eventId);
        byte[] excelBytes = createExcelWorkbook(salesData);
        auditSalesReportExport(organizerId, eventId);
        log.info("Sales report Excel generated: eventId={}, size={} bytes", eventId, excelBytes.length);
        return excelBytes;
    }

    @Override
    public String generateSalesReportFilename(String eventName) {
        String sanitized = sanitizeForFilename(eventName);
        String timestamp = LocalDateTime.now().format(FILENAME_DATE_FORMAT);
        return String.format("%s_sales_report_%s.xlsx", sanitized, timestamp);
    }

    private byte[] createExcelWorkbook(Map<String, Object> salesData) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sales Report");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);

            int rowNum = 0;

            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Event Sales Report");
            titleCell.setCellStyle(headerStyle);

            rowNum++;
            Row eventRow = sheet.createRow(rowNum++);
            eventRow.createCell(0).setCellValue("Event:");
            eventRow.createCell(1).setCellValue((String) salesData.get("eventName"));

            Row timestampRow = sheet.createRow(rowNum++);
            timestampRow.createCell(0).setCellValue("Generated:");
            timestampRow.createCell(1).setCellValue(LocalDateTime.now().toString());

            rowNum++;
            Row summaryHeaderRow = sheet.createRow(rowNum++);
            Cell summaryHeader = summaryHeaderRow.createCell(0);
            summaryHeader.setCellValue("Summary");
            summaryHeader.setCellStyle(headerStyle);

            Row totalTicketsRow = sheet.createRow(rowNum++);
            totalTicketsRow.createCell(0).setCellValue("Total Tickets Sold:");
            Cell totalTicketsCell = totalTicketsRow.createCell(1);
            // H-12 FIX: POI requires double at the rendering layer — BigDecimal arithmetic
            // already completed above. intValue() is used for counts; doubleValue() for currency.
            totalTicketsCell.setCellValue((Integer) salesData.get("totalTicketsSold"));
            totalTicketsCell.setCellStyle(numberStyle);

            Row totalRevenueBeforeRow = sheet.createRow(rowNum++);
            totalRevenueBeforeRow.createCell(0).setCellValue("Total Revenue (Before Discount):");
            Cell totalRevenueBeforeCell = totalRevenueBeforeRow.createCell(1);
            totalRevenueBeforeCell.setCellValue(((BigDecimal) salesData.get("totalRevenueBeforeDiscount")).doubleValue());
            totalRevenueBeforeCell.setCellStyle(currencyStyle);

            Row totalDiscountRow = sheet.createRow(rowNum++);
            totalDiscountRow.createCell(0).setCellValue("Total Discount Given:");
            Cell totalDiscountCell = totalDiscountRow.createCell(1);
            totalDiscountCell.setCellValue(((BigDecimal) salesData.get("totalDiscountGiven")).doubleValue());
            totalDiscountCell.setCellStyle(currencyStyle);

            Row totalRevenueFinalRow = sheet.createRow(rowNum++);
            totalRevenueFinalRow.createCell(0).setCellValue("Final Revenue (After Discount):");
            Cell totalRevenueFinalCell = totalRevenueFinalRow.createCell(1);
            totalRevenueFinalCell.setCellValue(((BigDecimal) salesData.get("totalRevenueFinal")).doubleValue());
            totalRevenueFinalCell.setCellStyle(currencyStyle);

            rowNum++;
            Row breakdownHeaderRow = sheet.createRow(rowNum++);
            Cell breakdownHeader = breakdownHeaderRow.createCell(0);
            breakdownHeader.setCellValue("Ticket Type Breakdown");
            breakdownHeader.setCellStyle(headerStyle);

            Row tableHeaderRow = sheet.createRow(rowNum++);
            String[] headers = {
                    "Ticket Type", "Base Price", "Sold",
                    "Revenue (Before Discount)", "Discount Given", "Final Revenue", "Remaining"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell headerCell = tableHeaderRow.createCell(i);
                headerCell.setCellValue(headers[i]);
                headerCell.setCellStyle(headerStyle);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ticketTypeStats =
                    (List<Map<String, Object>>) salesData.get("ticketTypeBreakdown");

            for (Map<String, Object> typeStats : ticketTypeStats) {
                Row dataRow = sheet.createRow(rowNum++);
                dataRow.createCell(0).setCellValue((String) typeStats.get("ticketTypeName"));

                Cell basePriceCell = dataRow.createCell(1);
                basePriceCell.setCellValue(((BigDecimal) typeStats.get("basePrice")).doubleValue());
                basePriceCell.setCellStyle(currencyStyle);

                Cell soldCell = dataRow.createCell(2);
                soldCell.setCellValue((Integer) typeStats.get("sold"));
                soldCell.setCellStyle(numberStyle);

                Cell revenueBeforeCell = dataRow.createCell(3);
                revenueBeforeCell.setCellValue(((BigDecimal) typeStats.get("revenueBeforeDiscount")).doubleValue());
                revenueBeforeCell.setCellStyle(currencyStyle);

                Cell discountCell = dataRow.createCell(4);
                discountCell.setCellValue(((BigDecimal) typeStats.get("discountGiven")).doubleValue());
                discountCell.setCellStyle(currencyStyle);

                Cell revenueFinalCell = dataRow.createCell(5);
                revenueFinalCell.setCellValue(((BigDecimal) typeStats.get("revenueFinal")).doubleValue());
                revenueFinalCell.setCellStyle(currencyStyle);

                Cell remainingCell = dataRow.createCell(6);
                remainingCell.setCellValue((Integer) typeStats.get("remaining"));
                remainingCell.setCellStyle(numberStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();

        } catch (IOException ex) {
            log.error("Failed to generate Excel workbook", ex);
            throw new ReportGenerationException("Failed to generate sales report Excel", ex);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0"));
        return style;
    }

    /**
     * C-05/C-06 FIX: Capture the sanitized string FIRST, then apply substring
     * on its actual length — not on input.length(). The regex operations can
     * shorten the string (stripping special chars), so using input.length() as
     * the upper bound caused SIOOBE whenever the sanitized result was shorter
     * than the cap (50 chars).
     */
    private String sanitizeForFilename(String input) {
        if (input == null) return "unknown";
        String sanitized = input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "_")
                .replaceAll("-+", "_");
        // FIX: use sanitized.length(), not input.length()
        return sanitized.substring(0, Math.min(sanitized.length(), 50));
    }

    private void auditSalesReportExport(UUID organizerId, UUID eventId) {
        try {
            HttpServletRequest request = getCurrentRequest();
            User actor = userRepository.findById(organizerId)
                    .orElseGet(systemUserProvider::getSystemUser);
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.SALES_REPORT_EXPORTED)
                    .actor(actor)
                    .resourceType("Event")
                    .resourceId(eventId)
                    .details(String.format("Sales report exported for event: %s", eventId))
                    .ipAddress(extractClientIp(request))
                    .userAgent(extractUserAgent(request))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception ex) {
            log.error("Failed to audit sales report export: organizerId={}, eventId={}",
                    organizerId, eventId, ex);
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}