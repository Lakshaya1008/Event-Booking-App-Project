import { useEffect, useState } from "react";
import { useAuth } from "react-oidc-context";
import { jwtDecode } from "jwt-decode";

interface UseRolesReturn {
  isLoading: boolean;
  roles: string[];
  isOrganizer: boolean;
  isAttendee: boolean;
  isStaff: boolean;
}

interface JwtPayload {
  realm_access?: {
    roles?: string[];
  };
}

export const useRoles = (): UseRolesReturn => {
  const { isLoading: isAuthLoading, user } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [roles, setRoles] = useState<string[]>([]);
  const [isOrganizer, setIsOrganizer] = useState(false);
  const [isAttendee, setIsAttendee] = useState(false);
  const [isStaff, setIsStaff] = useState(false);

  useEffect(() => {
    setIsLoading(true);

    if (isAuthLoading || !user?.access_token) {
      setRoles([]);
      setIsOrganizer(false);
      setIsAttendee(false);
      setIsStaff(false);
      setIsLoading(isAuthLoading);
      return;
    }

    try {
      const payload = jwtDecode<JwtPayload>(user?.access_token);
      const allRoles = payload.realm_access?.roles || [];

      // Normalize roles to support both KEYCLOAK styles: ["ORGANIZER"] and ["ROLE_ORGANIZER"]
      const normalizedRoles = allRoles.map((r) =>
        r.startsWith("ROLE_") ? r.replace(/^ROLE_/, "") : r,
      );

      setRoles(normalizedRoles);
      setIsOrganizer(normalizedRoles.includes("ORGANIZER"));
      setIsAttendee(normalizedRoles.includes("ATTENDEE"));
      setIsStaff(normalizedRoles.includes("STAFF"));
    } catch (error) {
      console.error("Error parsing JWT: " + error);
      setRoles([]);
      setIsOrganizer(false);
      setIsAttendee(false);
      setIsStaff(false);
    } finally {
      setIsLoading(false);
    }
  }, [isAuthLoading, user?.access_token]);

  return {
    isLoading,
    roles,
    isOrganizer,
    isAttendee,
    isStaff,
  };
};
