/* Where the SmartCam server lives.
   ''                       → the same server that serves this page (npm start in /server).
   http://localhost:8787    → in the Android app: your PC over USB / emulator, after
                              `adb reverse tcp:8787 tcp:8787` (forwards the phone's port 8787 to your PC).
   http://192.168.x.x:8787  → your PC on Wi-Fi, for a phone without the cable (or use a deployed https URL).
   Settings → API info → "Server URL" overrides this on a device. */
window.SC = window.SC || {};
SC.config = {
  backend: window.Capacitor?.isNativePlatform?.() ? 'http://localhost:8787' : '',
};
