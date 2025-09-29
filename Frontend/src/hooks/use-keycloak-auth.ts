import { useKeycloak } from '@react-keycloak/web';

export function useKeycloakAuth() {
  const { keycloak, initialized } = useKeycloak();
  return {
    keycloak,
    initialized,
    isAuthenticated: keycloak?.authenticated,
    token: keycloak?.token,
    userInfo: keycloak?.tokenParsed,
    login: () => keycloak?.login(),
    logout: () => keycloak?.logout(),
  };
}

