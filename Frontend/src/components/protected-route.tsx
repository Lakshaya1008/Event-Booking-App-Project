import { ReactNode } from "react";
import { useAuth } from "react-oidc-context";
import { Navigate, useLocation } from "react-router";
import { useRoles } from "@/hooks/use-roles";

interface ProtectedRouteProperties {
  children: ReactNode;
  allowedRoles?: ("ORGANIZER" | "ATTENDEE" | "STAFF")[];
}

const ProtectedRoute: React.FC<ProtectedRouteProperties> = ({ children, allowedRoles }) => {
  const { isLoading, isAuthenticated } = useAuth();
  const { isLoading: isRolesLoading, roles } = useRoles();
  const location = useLocation();

  if (isLoading || isRolesLoading) {
    return <p>Loading...</p>;
  }

  if (!isAuthenticated) {
    localStorage.setItem(
      "redirectPath",
      globalThis.location.pathname + globalThis.location.search,
    );
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (allowedRoles && allowedRoles.length > 0) {
    const hasRole = roles.some((r) => allowedRoles.includes(r as any));
    if (!hasRole) {
      return <Navigate to="/" replace />;
    }
  }

  return children;
};

export default ProtectedRoute;
