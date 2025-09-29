export interface ErrorResponse {
  error: string;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type BackendError = ErrorResponse | DetailedErrorResponse;

export const isErrorResponse = (obj: unknown): obj is BackendError => {
  return (
    obj !== null &&
    typeof obj === "object" &&
    "error" in obj &&
    typeof (obj as Record<string, unknown>).error === "string"
  );
};

export enum EventStatusEnum {
  DRAFT = "DRAFT",
  PUBLISHED = "PUBLISHED",
  CANCELLED = "CANCELLED",
  COMPLETED = "COMPLETED",
}

export interface CreateTicketTypeRequest {
  name: string;
  price: number;
  description: string;
  totalAvailable?: number;
}

export interface CreateEventRequest {
  name: string;
  start?: Date;
  end?: Date;
  venue: string;
  salesStart?: Date;
  salesEnd?: Date;
  status: EventStatusEnum;
  ticketTypes: CreateTicketTypeRequest[];
}

export interface UpdateTicketTypeRequest {
  id: string | undefined;
  name: string;
  price: number;
  description: string;
  totalAvailable?: number;
}

export interface UpdateEventRequest {
  id: string;
  name: string;
  start?: Date;
  end?: Date;
  venue: string;
  salesStart?: Date;
  salesEnd?: Date;
  status: EventStatusEnum;
  ticketTypes: UpdateTicketTypeRequest[];
}

export interface TicketTypeSummary {
  id: string;
  name: string;
  price: number;
  description: string;
  totalAvailable?: number;
}

export interface EventSummary {
  id: string;
  name: string;
  start?: Date;
  end?: Date;
  venue: string;
  salesStart?: Date;
  salesEnd?: Date;
  status: EventStatusEnum;
  ticketTypes: TicketTypeSummary[];
}

export interface PublishedEventSummary {
  id: string;
  name: string;
  start?: Date;
  end?: Date;
  venue: string;
}

export interface TicketTypeDetails {
  id: string;
  name: string;
  price: number;
  description: string;
  totalAvailable?: number;
}

export interface EventDetails {
  id: string;
  name: string;
  start?: Date;
  end?: Date;
  venue: string;
  salesStart?: Date;
  salesEnd?: Date;
  status: EventStatusEnum;
  ticketTypes: TicketTypeDetails[];
  createdAt: Date;
  updatedAt: Date;
}

export interface SpringBootPagination<T> {
  content: T[]; // The actual data items for the current page
  pageable: {
    sort: {
      empty: boolean;
      sorted: boolean;
      unsorted: boolean;
    };
    offset: number;
    pageNumber: number;
    pageSize: number;
    paged: boolean;
    unpaged: boolean;
  };
  last: boolean; // Whether this is the last page
  totalElements: number; // Total number of items across all pages
  totalPages: number; // Total number of pages
  size: number; // Page size (items per page)
  number: number; // Current page number (zero-based)
  sort: {
    empty: boolean;
    sorted: boolean;
    unsorted: boolean;
  };
  first: boolean; // Whether this is the first page
  numberOfElements: number; // Number of items in the current page
  empty: boolean; // Whether the current page has no items
}

export interface PublishedEventTicketTypeDetails {
  id: string;
  name: string;
  price: number;
  description: string;
}

export interface PublishedEventDetails {
  id: string;
  name: string;
  start?: Date;
  end?: Date;
  venue: string;
  ticketTypes: PublishedEventTicketTypeDetails[];
}

export enum TicketStatus {
  PURCHASED = "PURCHASED",
  CANCELLED = "CANCELLED",
}

export interface TicketSummaryTicketType {
  id: string;
  name: string;
  price: number;
}

export interface TicketSummary {
  id: string;
  status: TicketStatus;
  ticketType: TicketSummaryTicketType;
}

export interface TicketDetails {
  id: string;
  status: TicketStatus;
  price: number;
  description: string;
  eventName: string;
  eventVenue: string;
  eventStart: Date;
  eventEnd: Date;
}

export enum TicketValidationMethod {
  QR_SCAN = "QR_SCAN",
  MANUAL = "MANUAL",
}

export enum TicketValidationStatus {
  VALID = "VALID",
  INVALID = "INVALID",
  EXPIRED = "EXPIRED",
}

export interface TicketValidationRequest {
  id: string;
  method: TicketValidationMethod;
}

export interface TicketValidationResponse {
  ticketId: string;
  status: TicketValidationStatus;
}

// NEW TYPES ADDED FOR API COMPLIANCE

// QR Code entity
export interface QrCode {
  id: string;
  ticketId: string;
  data: string;
}

// Extended ticket details with QR codes
export interface TicketDetailsWithQr extends TicketDetails {
  qrCodes: QrCode[];
}

// Purchase ticket request with quantity support
export interface PurchaseTicketRequest {
  quantity?: number; // Optional, defaults to 1, valid range 1-10
}

// Purchase ticket response - array of tickets
export interface PurchaseTicketResponse extends TicketSummary {}

// Sales dashboard data
export interface SalesDashboard {
  eventName: string;
  totalTicketsSold: number;
  totalRevenue: number;
  ticketTypeBreakdown: TicketTypeBreakdown[];
}

export interface TicketTypeBreakdown {
  ticketTypeName: string;
  totalAvailable: number;
  sold: number;
  remaining: number;
  revenue: number;
  price: number;
}

// Attendees report data
export interface AttendeesReport {
  eventName: string;
  totalAttendees: number;
  attendees: AttendeeInfo[];
}

export interface AttendeeInfo {
  attendeeName: string;
  attendeeEmail: string;
  ticketType: string;
  ticketStatus: string;
  purchaseDate: string;
  validationCount: number;
}

// Ticket type CRUD operations
export interface CreateTicketTypeDto {
  name: string;
  price: number;
  description: string;
  totalAvailable?: number;
}

export interface UpdateTicketTypeDto {
  id: string;
  name: string;
  price: number;
  description: string;
  totalAvailable?: number;
}

export interface TicketTypeResponseDto {
  id: string;
  name: string;
  price: number;
  description: string;
  totalAvailable?: number;
  eventId: string;
}

// Enhanced error response with more details
export interface DetailedErrorResponse {
  error: string;
  message?: string;
  statusCode?: number;
  statusDescription?: string;
  possibleCauses?: string[];
  solutions?: string[];
}