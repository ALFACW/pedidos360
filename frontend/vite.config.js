import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  base: './',
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    // Gateway local: enruta cada API al microservicio correspondiente
    proxy: {
      '/api/orders': 'http://localhost:8080',
      '/api/products': 'http://localhost:8081',
      '/api/users': 'http://localhost:8082',
      '/api/cart': 'http://localhost:8083',
    },
    allowedHosts: ['same-perfume-cloud.ngrok-free.dev', '.ngrok-free.dev'],
  },
});
