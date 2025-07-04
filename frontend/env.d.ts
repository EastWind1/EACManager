/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_TOKEN_SECRET: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
