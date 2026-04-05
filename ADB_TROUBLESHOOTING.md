# ADB Device Not Detected - Troubleshooting Guide

## Issue: `adb devices` shows no devices

## Quick Fixes (Try in Order)

### 1. Check USB Connection Mode
On your Android phone:
- Swipe down notifications
- Look for "USB" notification
- Tap it and select **"File Transfer" or "MTP"** mode
  (NOT "Charging only")

### 2. Authorize USB Debugging
When you connect your phone, you should see a popup:
- **"Allow USB debugging?"** with your computer's fingerprint
- Tap **"Always allow from this computer"**
- Tap **"OK"**

If you don't see this popup, try:
```powershell
C:\Users\dibya\AppData\Local\Android\Sdk\platform-tools\adb.exe kill-server
C:\Users\dibya\AppData\Local\Android\Sdk\platform-tools\adb.exe start-server
```
Then unplug and replug your phone.

### 3. Revoke and Re-authorize
On your phone:
1. Go to **Settings → Developer Options**
2. Find **"Revoke USB debugging authorizations"**
3. Tap it
4. Unplug and replug USB cable
5. Accept the new authorization dialog

### 4. Install USB Drivers (Windows)

Most Android phones need specific USB drivers on Windows:

**For Google Pixel/Nexus:**
- Use Google USB Driver (included in Android SDK)

**For Samsung:**
- Install Samsung USB Driver: https://developer.samsung.com/mobile/android-usb-driver.html

**For other brands:**
- Search "YourBrand USB Driver" and install from manufacturer website

**Generic Method:**
```powershell
# Check if device shows in Device Manager
devmgmt.msc
```
Look for your phone under "Other devices" or with a yellow warning icon.

### 5. Try Different USB Cable/Port
- Use the original USB cable (some cables are charge-only)
- Try a different USB port on your computer
- Avoid USB hubs, connect directly to PC

### 6. Restart ADB Server
```powershell
C:\Users\dibya\AppData\Local\Android\Sdk\platform-tools\adb.exe kill-server
C:\Users\dibya\AppData\Local\Android\Sdk\platform-tools\adb.exe start-server
C:\Users\dibya\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
```

### 7. Check Developer Options Settings
On your phone, ensure:
- ✅ Developer Options is **ON**
- ✅ USB Debugging is **ON**
- ✅ Install via USB is **ON** (if available)

## Expected Output When Working

When successful, you should see:
```
List of devices attached
ABC123456789    device
```

The device ID and "device" status means it's ready!

## Alternative: Manual APK Installation (Skip ADB)

If ADB troubleshooting is taking too long, just install manually:

1. Copy `d:\ANTI\PandalFinder\app\build\outputs\apk\debug\app-debug.apk` to your phone
2. On your phone, open the APK file
3. Enable "Install from Unknown Sources" when prompted
4. Install!

This works just as well and is often easier! 😊
