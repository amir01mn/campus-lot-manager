/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Set to "false" to hide the demo-account sign-in buttons. */
  readonly VITE_SHOW_DEMO_ACCOUNTS?: string;
  /** Set to "false" to hide only the super-manager sign-in button (its password isn't the dev default). */
  readonly VITE_SHOW_SUPER_MANAGER_DEMO?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
