import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import {defineConfig} from 'vite';

export default defineConfig(() => {
  return {
    plugins: [react(), tailwindcss()],
    // sockjs-client (STOMP transport) expects a Node-style `global`
    define: {
      global: 'globalThis',
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    server: {
      // Tenant subdomains resolve against a public wildcard that points at
      // loopback, so http://sunrise.localtest.me:3000 works with no /etc/hosts
      // editing. Vite 6 rejects Host headers it does not recognise, hence this.
      allowedHosts: ['localhost', '.localtest.me'],
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modify—file watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
      // Disable file watching when DISABLE_HMR is true to save CPU during agent edits.
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
      // Proxy API requests to the Spring Cloud Gateway during development
      proxy: {
        '/api': {
          target: process.env.VITE_PROXY_TARGET || 'http://localhost:8081',
          // MUST stay false. changeOrigin: true rewrites the Host header to the
          // proxy target, which erases the tenant label before the gateway ever
          // sees it — tenant resolution would silently never fire in development
          // and the subdomain path would only ever be exercised in staging.
          changeOrigin: false,
        },
        '/ws': {
          target: process.env.VITE_PROXY_TARGET || 'http://localhost:8081',
          ws: true,
          changeOrigin: false,
        },
      },
    },
  };
});