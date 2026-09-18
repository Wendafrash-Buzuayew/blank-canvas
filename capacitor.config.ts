import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.safaricomet.qrserve',
  appName: 'com.safaricomet.qrserve',
  webDir: 'dist',
  server: {
    // Windows Wi-Fi adapter IP (LAN), not the WSL vEthernet IP — phones on
    // the same Wi-Fi network reach the dev server through this one.
    url: 'http://192.168.8.135:3000',
    cleartext: true
  }
};

export default config;
