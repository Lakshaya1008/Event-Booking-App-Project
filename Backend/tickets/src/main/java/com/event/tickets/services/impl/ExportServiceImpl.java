package com.event.tickets.services.impl;

import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.repositories.AuditLogRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.EventService;
import com.event.tickets.services.ExportService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;

/**
 * Export Service Implementation - READ-ONLY REPORT GENERATION
 *
 * PRINCIPLES:
 * - Reuses existing EventService.getSalesDashboard() for data
 * - NO duplicate queries or parallel business logic
 * - All exports are IDEMPOTENT (same content for same state)
 * - READ-ONLY operations, no side effects
 * - Authorization enforced via existing AuthorizationService
 *
 * AUDIT LOGGING:
 * - All export operations audited
 * - Logs include actor, resource, timestamp
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportServiceImpl implements ExportService {

  private final EventService eventService;
  private final AuditLogRepository auditLogRepository;
  private final UserRepository userRepository;

  private static final DateTimeFormatter FILENAME_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  @Override
  public byte[] generateSalesReportExcel(UUID organizerId, UUID eventId) {
    log.info("Generating sales report Excel: organizerId={}, eventId={}", organizerId, eventId);

    // CRITICAL: Reuse existing sales dashboard service
    // This ensures authorization check and consistent data source
    Map<String, Object> salesData = eventService.getSalesDashboard(organizerId, eventId);

    // Generate Excel workbook
    byte[] excelBytes = createExcelWorkbook(salesData);

    // Audit log: Sales report exported
    auditSalesReportExport(organizerId, eventId);

    log.info("Sales report Excel generated successfully: eventId={}, size={} bytes",
        eventId, excelBytes.length);

    return excelBytes;
  }

  @Override
  public String generateSalesReportFilename(String eventName) {
    String sanitizedEventName = sanitizeForFilename(eventName);
    String timestamp = LocalDateTime.now().format(FILENAME_DATE_FORMAT);

    return String.format("%s_sales_report_%s.xlsx", sanitizedEventName, timestamp);
  }

  /**
   * Creates Excel workbook from sales dashboard data.
   * Uses Apache POI (XSSF) for .xlsx format.
   */
  private byte[] createExcelWorkbook(Map<String, Object> salesData) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      Sheet sheet = workbook.createSheet("Sales Report");

      // Create styles
      CellStyle headerStyle = createHeaderStyle(workbook);
      CellStyle currencyStyle = createCurrencyStyle(workbook);
      CellStyle numberStyle = createNumberStyle(workbook);

      int rowNum = 0;

      // Title
      Row titleRow = sheet.createRow(rowNum++);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue("Event Sales Report");
      titleCell.setCellStyle(headerStyle);

      // Event name
      rowNum++;
      Row eventRow = sheet.createRow(rowNum++);
      eventRow.createCell(0).setCellValue("Event:");
      eventRow.createCell(1).setCellValue((String) salesData.get("eventName"));

      // Generated timestamp
      Row timestampRow = sheet.createRow(rowNum++);
      timestampRow.createCell(0).setCellValue("Generated:");
      timestampRow.createCell(1).setCellValue(LocalDateTime.now().toString());

      // Summary section
      rowNum++;
      Row summaryHeaderRow = sheet.createRow(rowNum++);
      Cell summaryHeader = summaryHeaderRow.createCell(0);
      summaryHeader.setCellValue("Summary");
      summaryHeader.setCellStyle(headerStyle);

      Row totalTicketsRow = sheet.createRow(rowNum++);
      totalTicketsRow.createCell(0).setCellValue("Total Tickets Sold:");
      Cell totalTicketsCell = totalTicketsRow.createCell(1);
      totalTicketsCell.setCellValue(((Number) salesData.get("totalTicketsSold")).doubleValue());
      totalTicketsCell.setCellStyle(numberStyle);

      Row totalRevenueRow = sheet.createRow(rowNum++);
      totalRevenueRow.createCell(0).setCellValue("Total Revenue:");
      Cell totalRevenueCell = totalRevenueRow.createCell(1);
      totalRevenueCell.setCellValue(((Number) salesData.get("totalRevenue")).doubleValue());
      totalRevenueCell.setCellStyle(currencyStyle);

      // Ticket type breakdown section
      rowNum++;
      Row breakdownHeaderRow = sheet.createRow(rowNum++);
      Cell breakdownHeader = breakdownHeaderRow.createCell(0);
      breakdownHeader.setCellValue("Ticket Type Breakdown");
      breakdownHeader.setCellStyle(headerStyle);

      // Table headers
      Row tableHeaderRow = sheet.createRow(rowNum++);
      String[] headers = {"Ticket Type", "Price", "Total Available", "Sold", "Remaining", "Revenue"};
      for (int i = 0; i < headers.length; i++) {
        Cell headerCell = tableHeaderRow.createCell(i);
        headerCell.setCellValue(headers[i]);
        headerCell.setCellStyle(headerStyle);
      }

      // Table data
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> ticketTypeStats =
          (List<Map<String, Object>>) salesData.get("ticketTypeBreakdown");

      for (Map<String, Object> typeStats : ticketTypeStats) {
        Row dataRow = sheet.createRow(rowNum++);

        dataRow.createCell(0).setCellValue((String) typeStats.get("ticketTypeName"));

        Cell priceCell = dataRow.createCell(1);
        priceCell.setCellValue(((Number) typeStats.get("price")).doubleValue());
        priceCell.setCellStyle(currencyStyle);

        Cell availableCell = dataRow.createCell(2);
        availableCell.setCellValue(((Number) typeStats.get("totalAvailable")).doubleValue());
        availableCell.setCellStyle(numberStyle);

        Cell soldCell = dataRow.createCell(3);
        soldCell.setCellValue(((Number) typeStats.get("sold")).doubleValue());
        soldCell.setCellStyle(numberStyle);

        Cell remainingCell = dataRow.createCell(4);
        remainingCell.setCellValue(((Number) typeStats.get("remaining")).doubleValue());
        remainingCell.setCellStyle(numberStyle);

        Cell revenueCell = dataRow.createCell(5);
        revenueCell.setCellValue(((Number) typeStats.get("revenue")).doubleValue());
        revenueCell.setCellStyle(currencyStyle);
      }

      // Auto-size columns
      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(baos);
      return baos.toByteArray();

    } catch (IOException ex) {
      log.error("Failed to generate Excel workbook for sales report", ex);
      throw new RuntimeException("Failed to generate sales report Excel", ex);
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
   * Sanitizes string for use in filename.
   * Converts to lowercase, replaces spaces with underscores, removes special chars.
   */
  private String sanitizeForFilename(String input) {
    if (input == null) {
      return "unknown";
    }
    return input.toLowerCase()
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("\\s+", "_")
        .replaceAll("-+", "_")
        .substring(0, Math.min(input.length(), 50)); // Max 50 chars
  }

  /**
   * Audits sales report export with actor, event, and timestamp.
   */
  private void auditSalesReportExport(UUID organizerId, UUID eventId) {
    try {
      HttpServletRequest request = getCurrentRequest();
      String ipAddress = request != null ? extractClientIp(request) : "unknown";
      String userAgent = request != null ? extractUserAgent(request) : "unknown";

      AuditLog auditLog = AuditLog.builder()
          .action(AuditAction.SALES_REPORT_EXPORTED)
          .actor(userRepository.findById(organizerId).orElse(null))
          .resourceType("Event")
          .resourceId(eventId)
          .details(String.format("Sales report exported for event: %s", eventId))
          .ipAddress(ipAddress)
          .userAgent(userAgent)
          .build();

      auditLogRepository.save(auditLog);

      log.info("Audited sales report export: organizerId={}, eventId={}", organizerId, eventId);
    } catch (Exception ex) {
      log.error("Failed to audit sales report export: organizerId={}, eventId={}",
          organizerId, eventId, ex);
      // Don't fail the operation if audit logging fails
    }
  }

  private HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
  }
}
