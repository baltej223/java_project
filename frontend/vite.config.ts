import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/tasks': 'http://localhost:8080',
      '/jobs': 'http://localhost:8080',
      '/dlq': 'http://localhost:8080',
      '/healthz': 'http://localhost:8080',
    },
  },
})
