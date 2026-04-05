# Installation Instructions

## ✅ Build Successful!

Your APK has been created at:
```
d:\ANTI\PandalFinder\app\build\outputs\apk\debug\app-debug.apk
```

## Installation Options

### Option 1: Using ADB (If you have a device connected)

Since `adb` is not in your PATH, use the full path:

```powershell
C:\Users\dibya\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
C:\Users\dibya\AppData\Local\Android\Sdk\platform-tools\adb.exe install app\build\outputs\apk\debug\app-debug.apk
```

**Steps:**
1. Enable USB Debugging on your Android phone:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times to enable Developer Options
   - Go to Settings → Developer Options
   - Enable "USB Debugging"
2. Connect phone via USB
3. Run the commands above

### Option 2: Manual Transfer (Easiest)

1. Copy the APK file to your phone:
   - Via USB: Copy `app-debug.apk` to your phone's Download folder
   - Via file sharing app (e.g., Google Drive, Telegram)
   
2. On your phone:
   - Open the file manager
   - Navigate to the APK file
   - Tap to install
   - If prompted, enable "Install from Unknown Sources" for your file manager

### Option 3: Add ADB to PATH (For Future Convenience)

Add to your system PATH:
```
C:\Users\dibya\AppData\Local\Android\Sdk\platform-tools
```

Then restart VS Code and you can use just `adb` command.

## Testing the App

Once installed:
1. Open "Pandal Finder" app
2. Tap "Find Nearby Pandals"
3. Grant location permissions when prompted
4. The app will show nearby pandals (sorted by distance)
5. Tap any pandal to navigate with Google Maps

**Note:** For best GPS accuracy, test outdoors or near a window!
