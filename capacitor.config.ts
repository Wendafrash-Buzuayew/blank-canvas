import type { CapacitorConfig } from '@capacitor/cli';

// Live-reload against a dev server only when explicitly requested, e.g.
// CAP_SERVER_URL=http://192.168.8.135:3000 npx cap run android
// (use the host's LAN IP so phones on the same Wi-Fi can reach it).
// Release builds leave it unset and ship the bundled `dist` over https.
const devServerUrl = process.env.CAP_SERVER_URL;

const config: CapacitorConfig = {
  appId: 'com.safaricomet.qrserve',
  appName: 'QRServe',
  webDir: 'dist',
  ...(devServerUrl
    ? {
        server: {
          url: devServerUrl,
          cleartext: devServerUrl.startsWith('http://'),
        },
      }
    : {}),
};

export default config;
