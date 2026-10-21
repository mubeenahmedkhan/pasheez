import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')

  // Fail the build rather than emit a bundle that points nowhere. Without this, a
  // production build with no API URL would compile happily and then break in the
  // visitor's browser — the expensive place to discover a missing variable.
  if (!env.VITE_API_BASE_URL) {
    throw new Error(
      `VITE_API_BASE_URL is not set for mode "${mode}". ` +
        `For local work, copy .env.example to .env.development. ` +
        `For a deploy, set it in the hosting provider's environment variables.`,
    )
  }

  return { plugins: [react()] }
})
