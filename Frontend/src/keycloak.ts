import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: 'http://localhost:9090/', // Keycloak server URL
  realm: 'event-ticket-platform', // Your Keycloak realm
  clientId: 'frontend', // Your Keycloak client (must match Keycloak config)
});

export default keycloak;

