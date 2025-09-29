import {
  CreateEventRequest,
  EventDetails,
  EventSummary,
  isErrorResponse,
  PublishedEventDetails,
  PublishedEventSummary,
  SpringBootPagination,
  TicketDetails,
  TicketSummary,
  TicketValidationRequest,
  TicketValidationResponse,
  UpdateEventRequest,
  PurchaseTicketRequest,
  PurchaseTicketResponse,
  SalesDashboard,
  AttendeesReport,
  TicketTypeResponseDto,
  CreateTicketTypeDto,
  UpdateTicketTypeDto,
} from "@/domain/domain";

// Extracts a readable backend error message if available
function extractErrorMessage(responseBody: unknown): string {
  try {
    if (responseBody && typeof responseBody === "object" && responseBody !== null) {
      const errorObj = responseBody as Record<string, unknown>;
      if (typeof errorObj.message === "string" && errorObj.message.length > 0) {
        return errorObj.message;
      }
      if (typeof errorObj.error === "string" && errorObj.error.length > 0) {
        return errorObj.error;
      }
    }
  } catch {
    // ignore parsing errors
  }
  return "An unknown error occurred";
}

export const createEvent = async (
  accessToken: string,
  request: CreateEventRequest,
): Promise<void> => {
  const response = await fetch("/api/v1/events", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("An unknown error occurred");
    }
  }
};

export const updateEvent = async (
  accessToken: string,
  id: string,
  request: UpdateEventRequest,
): Promise<void> => {
  const response = await fetch(`/api/v1/events/${id}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("An unknown error occurred");
    }
  }
};

export const listEvents = async (
  accessToken: string,
  page: number,
  size: number = 2,
  sort?: string,
): Promise<SpringBootPagination<EventSummary>> => {
  const sortQuery = sort ? `&sort=${encodeURIComponent(sort)}` : "";
  const response = await fetch(`/api/v1/events?page=${page}&size=${size}${sortQuery}`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("An unknown error occurred");
    }
  }

  return responseBody as SpringBootPagination<EventSummary>;
};

export const getEvent = async (
  accessToken: string,
  id: string,
): Promise<EventDetails> => {
  const response = await fetch(`/api/v1/events/${id}`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("An unknown error occurred");
    }
  }

  return responseBody as EventDetails;
};

export const deleteEvent = async (
  accessToken: string,
  id: string,
): Promise<void> => {
  const response = await fetch(`/api/v1/events/${id}`, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
  });

  if (!response.ok) {
    const responseBody = await response.json();
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("An unknown error occurred");
    }
  }
};


export const listPublishedEvents = async (
  accessToken: string | undefined,
  page: number,
  size: number = 4,
  sort?: string,
): Promise<SpringBootPagination<PublishedEventSummary>> => {
  const sortQuery = sort ? `&sort=${encodeURIComponent(sort)}` : "";
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (accessToken) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }
  const response = await fetch(`/api/v1/published-events?page=${page}&size=${size}${sortQuery}`, {
    method: "GET",
    headers,
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("An unknown error occurred");
    }
  }

  return responseBody as SpringBootPagination<PublishedEventSummary>;
};


export const searchPublishedEvents = async (
  accessToken: string | undefined,
  query: string,
  page: number,
  size: number = 4,
  sort?: string,
): Promise<SpringBootPagination<PublishedEventSummary>> => {
  const sortQuery = sort ? `&sort=${encodeURIComponent(sort)}` : "";
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (accessToken) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }
  const response = await fetch(
    `/api/v1/published-events?q=${encodeURIComponent(query)}&page=${page}&size=${size}${sortQuery}`,
    {
      method: "GET",
      headers,
    },
  );

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("An unknown error occurred");
    }
  }

  return responseBody as SpringBootPagination<PublishedEventSummary>;
};

export const getPublishedEvent = async (
  id: string,
): Promise<PublishedEventDetails> => {
  const response = await fetch(`/api/v1/published-events/${id}`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
    },
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("An unknown error occurred");
    }
  }

  return responseBody as PublishedEventDetails;
};

export const purchaseTicket = async (
  accessToken: string,
  eventId: string,
  ticketTypeId: string,
  quantity: number = 1,
): Promise<PurchaseTicketResponse[]> => {
  const request: PurchaseTicketRequest = { quantity };

  const response = await fetch(
    `/api/v1/events/${eventId}/ticket-types/${ticketTypeId}/tickets`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    const responseBody = await response.json();
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("An unknown error occurred");
    }
  }

  return await response.json() as PurchaseTicketResponse[];
};

export const listTickets = async (
  accessToken: string,
  page: number,
  size: number = 8,
  sort?: string,
): Promise<SpringBootPagination<TicketSummary>> => {
  const sortQuery = sort ? `&sort=${encodeURIComponent(sort)}` : "";
  const response = await fetch(`/api/v1/tickets?page=${page}&size=${size}${sortQuery}`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("An unknown error occurred");
    }
  }

  return responseBody as SpringBootPagination<TicketSummary>;
};

export const getTicket = async (
  accessToken: string,
  id: string,
): Promise<TicketDetails> => {
  const response = await fetch(`/api/v1/tickets/${id}`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("An unknown error occurred");
    }
  }

  return responseBody as TicketDetails;
};

export const getTicketQr = async (
  accessToken: string,
  id: string,
): Promise<Blob> => {
  const response = await fetch(`/api/v1/tickets/${id}/qr-codes`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.ok) {
    return await response.blob();
  } else {
    throw new Error("Unable to get ticket QR code");
  }
};

export const validateTicket = async (
  accessToken: string,
  request: TicketValidationRequest,
): Promise<TicketValidationResponse> => {
  const response = await fetch(`/api/v1/ticket-validations`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("An unknown error occurred");
    }
  }

  return responseBody as TicketValidationResponse;
};

// Sales Dashboard
export const getSalesDashboard = async (
  accessToken: string,
  eventId: string,
): Promise<SalesDashboard> => {
  const response = await fetch(`/api/v1/events/${eventId}/sales-dashboard`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("Failed to fetch sales dashboard");
    }
  }

  return responseBody as SalesDashboard;
};

// Attendees Report
export const getAttendeesReport = async (
  accessToken: string,
  eventId: string,
): Promise<AttendeesReport> => {
  const response = await fetch(`/api/v1/events/${eventId}/attendees-report`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("Failed to fetch attendees report");
    }
  }

  return responseBody as AttendeesReport;
};

// Ticket Type CRUD Operations
export const createTicketType = async (
  accessToken: string,
  eventId: string,
  request: CreateTicketTypeDto,
): Promise<TicketTypeResponseDto> => {
  const response = await fetch(`/api/v1/events/${eventId}/ticket-types`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("Failed to create ticket type");
    }
  }

  return responseBody as TicketTypeResponseDto;
};

export const listTicketTypes = async (
  accessToken: string,
  eventId: string,
): Promise<TicketTypeResponseDto[]> => {
  const response = await fetch(`/api/v1/events/${eventId}/ticket-types`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
  });

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("Failed to fetch ticket types");
    }
  }

  return responseBody as TicketTypeResponseDto[];
};

export const getTicketType = async (
  accessToken: string,
  eventId: string,
  ticketTypeId: string,
): Promise<TicketTypeResponseDto> => {
  const response = await fetch(
    `/api/v1/events/${eventId}/ticket-types/${ticketTypeId}`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
    },
  );

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("Failed to fetch ticket type");
    }
  }

  return responseBody as TicketTypeResponseDto;
};

export const updateTicketType = async (
  accessToken: string,
  eventId: string,
  ticketTypeId: string,
  request: UpdateTicketTypeDto,
): Promise<TicketTypeResponseDto> => {
  const response = await fetch(
    `/api/v1/events/${eventId}/ticket-types/${ticketTypeId}`,
    {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("Failed to update ticket type");
    }
  }

  return responseBody as TicketTypeResponseDto;
};

export const deleteTicketType = async (
  accessToken: string,
  eventId: string,
  ticketTypeId: string,
): Promise<void> => {
  const response = await fetch(
    `/api/v1/events/${eventId}/ticket-types/${ticketTypeId}`,
    {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
    },
  );

  if (!response.ok) {
    const responseBody = await response.json();
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("Failed to delete ticket type");
    }
  }
};

// Ticket Validation History
export const getEventValidations = async (
  accessToken: string,
  eventId: string,
  page: number = 0,
  size: number = 20,
  sort?: string,
): Promise<SpringBootPagination<TicketValidationResponse>> => {
  const sortQuery = sort ? `&sort=${encodeURIComponent(sort)}` : "";
  const response = await fetch(
    `/api/v1/ticket-validations/events/${eventId}?page=${page}&size=${size}${sortQuery}`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
    },
  );

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("Failed to fetch event validations");
    }
  }

  return responseBody as SpringBootPagination<TicketValidationResponse>;
};

export const getTicketValidations = async (
  accessToken: string,
  ticketId: string,
): Promise<TicketValidationResponse[]> => {
  const response = await fetch(
    `/api/v1/ticket-validations/tickets/${ticketId}`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
    },
  );

  const responseBody = await response.json();

  if (!response.ok) {
    if (isErrorResponse(responseBody)) {
      throw new Error(extractErrorMessage(responseBody));
    } else {
      console.error(JSON.stringify(responseBody));
      throw new Error("Failed to fetch ticket validations");
    }
  }

  return responseBody as TicketValidationResponse[];
};