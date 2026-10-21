/// <reference types="vite/client" />

// Vite types arbitrary VITE_* variables as `any`. Declaring them here gets real
// type-checking and autocomplete for our own configuration.
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
