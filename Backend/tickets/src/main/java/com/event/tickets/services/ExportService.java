package com.event.tickets.services;

import java.util.UUID;

/**
 * Export Service - READ-ONLY REPORT GENERATION
 *
 * Handles Excel and other export formats for reports.
 *
 * CRITICAL RULE:
 * - MUST reuse existing service/data (no duplicate queries)
 * - MUST NOT create parallel business logic
 * - Exports are READ-ONLY, IDEMPOTENT operations
 *
 * ACCESS CONTROL:
 * - ORGANIZER: Must own the event
 * - ADMIN: NO bypass
 * - ATTENDEE/STAFF: NO access
 */
public interface ExportService {

  /**
   * Generates sales report as Excel (.xlsx) file.
   *
   * REUSES: EventService.getSalesDashboard() for data
   * IDEMPOTENT: Same content every time for same event state
   * READ-ONLY: No state changes
   *
   * ACCESS CONTROL:
   * - Organizer must own the event (checked via AuthorizationService)
   * - ADMIN does NOT bypass ownership check
   *
   * @param organizerId The organizer requesting the report
   * @param eventId The event ID
   * @return Excel file bytes (.xlsx format)
   */
  byte[] generateSalesReportExcel(UUID organizerId, UUID eventId);

  /**
   * Generates sanitized filename for sales report export.
   * Format: <event-name>_sales_report_<yyyyMMdd_HHmmss>.xlsx
   *
   * @param eventName The event name
   * @return Sanitized filename
   */
  String generateSalesReportFilename(String eventName);
}
