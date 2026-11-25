import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  base: './',
  plugins: [react(), tailwindcss()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    // Gateway local: enruta cada API al microservicio correspondiente
    proxy: {
      '/api/v1/orders': 'http://localhost:8080',
      '/api/v1/products': 'http://localhost:8081',
      '/api/v1/users': 'http://localhost:8082',
      '/api/v1/cart': 'http://localhost:8083',
    },
    allowedHosts: ['same-perfume-cloud.ngrok-free.dev', '.ngrok-free.dev'],
  },
});
