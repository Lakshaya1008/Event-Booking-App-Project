/// <reference types="vite/client" />

interface ImportMetaEnv {
	readonly VITE_KEYCLOAK_AUTHORITY: string;
	readonly VITE_KEYCLOAK_CLIENT_ID: string;
	readonly VITE_REDIRECT_URI: string;
	readonly VITE_POST_LOGOUT_REDIRECT_URI: string;
	readonly VITE_API_BASE_URL: string;
}

interface ImportMeta {
	readonly env: ImportMetaEnv;
}
