import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In sviluppo le chiamate /api vengono girate al backend Spring Boot (niente CORS da configurare)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
