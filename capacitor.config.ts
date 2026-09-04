import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.safaricomet.qrserve',
  appName: 'com.safaricomet.qrserve',
  webDir: 'dist',
  server: {
    // Replace with your exact Windows IP and development server port
    url: 'http://192.168.1.45:5173', 
    cleartext: true
  }
};

export default config;
