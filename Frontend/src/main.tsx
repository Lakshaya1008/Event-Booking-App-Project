import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import AttendeeLandingPage from "./pages/attendee-landing-page.tsx";
import { createBrowserRouter, RouterProvider } from "react-router";
import OrganizersLandingPage from "./pages/organizers-landing-page.tsx";
import DashboardManageEventPage from "./pages/dashboard-manage-event-page.tsx";
import LoginPage from "./pages/login-page.tsx";
import ProtectedRoute from "./components/protected-route.tsx";
import CallbackPage from "./pages/callback-page.tsx";
import DashboardListEventsPage from "./pages/dashboard-list-events-page.tsx";
import PublishedEventsPage from "./pages/published-events-page.tsx";
import PurchaseTicketPage from "./pages/purchase-ticket-page.tsx";
import DashboardListTickets from "./pages/dashboard-list-tickets.tsx";
import DashboardPage from "./pages/dashboard-page.tsx";
import DashboardViewTicketPage from "./pages/dashboard-view-ticket-page.tsx";
import DashboardValidateQrPage from "./pages/dashboard-validate-qr-page.tsx";
import DashboardSalesDashboardPage from "./pages/dashboard-sales-dashboard-page.tsx";
import DashboardAttendeesReportPage from "./pages/dashboard-attendees-report-page.tsx";
import DashboardValidationsByEventPage from "./pages/dashboard-validations-by-event-page.tsx";
import DashboardValidationsByTicketPage from "./pages/dashboard-validations-by-ticket-page.tsx";
import { AuthProvider } from "react-oidc-context";
import { ErrorBoundary } from "./components/error-boundary";

const router = createBrowserRouter([
  {
    path: "/",
    Component: AttendeeLandingPage,
  },
  {
    path: "/callback",
    Component: CallbackPage,
  },
  {
    path: "/login",
    Component: LoginPage,
  },
  {
    path: "/events/:id",
    Component: PublishedEventsPage,
  },
  {
    path: "/events/:eventId/purchase/:ticketTypeId",
    element: (
      <ProtectedRoute allowedRoles={["ATTENDEE"]}>
        <PurchaseTicketPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/organizers",
    Component: OrganizersLandingPage,
  },
  {
    path: "/dashboard",
    element: (
      <ProtectedRoute allowedRoles={["ORGANIZER", "ATTENDEE", "STAFF"]}>
        <DashboardPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/dashboard/events",
    element: (
      <ProtectedRoute allowedRoles={["ORGANIZER"]}>
        <DashboardListEventsPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/dashboard/tickets",
    element: (
      <ProtectedRoute allowedRoles={["ATTENDEE"]}>
        <DashboardListTickets />
      </ProtectedRoute>
    ),
  },
  {
    path: "/dashboard/tickets/:id",
    element: (
      <ProtectedRoute allowedRoles={["ATTENDEE"]}>
        <DashboardViewTicketPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/dashboard/validate-qr",
    element: (
      <ProtectedRoute allowedRoles={["STAFF"]}>
        <DashboardValidateQrPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/dashboard/validations/events/:eventId",
    element: (
      <ProtectedRoute allowedRoles={["STAFF"]}>
        <DashboardValidationsByEventPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/dashboard/validations/tickets/:ticketId",
    element: (
      <ProtectedRoute allowedRoles={["STAFF"]}>
        <DashboardValidationsByTicketPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/dashboard/events/create",
    element: (
      <ProtectedRoute allowedRoles={["ORGANIZER"]}>
        <DashboardManageEventPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/dashboard/events/update/:id",
    element: (
      <ProtectedRoute allowedRoles={["ORGANIZER"]}>
        <DashboardManageEventPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/dashboard/events/:eventId/sales-dashboard",
    element: (
      <ProtectedRoute allowedRoles={["ORGANIZER"]}>
        <DashboardSalesDashboardPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/dashboard/events/:eventId/attendees-report",
    element: (
      <ProtectedRoute allowedRoles={["ORGANIZER"]}>
        <DashboardAttendeesReportPage />
      </ProtectedRoute>
    ),
  },
]);

const root = createRoot(document.getElementById("root")!);
root.render(
  <StrictMode>
    <ErrorBoundary>
      <AuthProvider
        authority={import.meta.env.VITE_KEYCLOAK_AUTHORITY}
        client_id={import.meta.env.VITE_KEYCLOAK_CLIENT_ID}
        redirect_uri={import.meta.env.VITE_REDIRECT_URI}
        post_logout_redirect_uri={import.meta.env.VITE_POST_LOGOUT_REDIRECT_URI}
        response_type="code"
        scope="openid profile email"
      >
        <RouterProvider router={router} />
      </AuthProvider>
    </ErrorBoundary>
  </StrictMode>
);
