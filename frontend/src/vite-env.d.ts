/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Set to "false" to hide the demo-account sign-in buttons. */
  readonly VITE_SHOW_DEMO_ACCOUNTS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
