# Android Pandal Finder - Build & Run Instructions

## Prerequisites

Before you can build the app in VS Code, ensure you have:

### 1. **Java Development Kit (JDK)**
- Install JDK 11 or higher
- Download from: https://adoptium.net/
- Verify installation: `java -version`

### 2. **Android SDK Command Line Tools**
- Download from: https://developer.android.com/studio#command-tools
- Extract to a folder (e.g., `C:\Android\cmdline-tools`)
- Set environment variable: `ANDROID_HOME=C:\Android`
- Add to PATH: `%ANDROID_HOME%\cmdline-tools\latest\bin` and `%ANDROID_HOME%\platform-tools`

### 3. **Android SDK Components**
Install required SDK components using sdkmanager:
```bash
sdkmanager "platform-tools" "platforms;android-33" "build-tools;33.0.0"
```

### 4. **VS Code Extensions (Optional but Recommended)**
- Kotlin Language Support
- Gradle for Java

## Building the App

### Step 1: Navigate to Project Directory
```bash
cd d:\ANTI\PandalFinder
```

### Step 2: Build Debug APK
```powershell
.\gradlew.bat assembleDebug
```

This will compile the app and create an APK file at:
```
app\build\outputs\apk\debug\app-debug.apk
```

### Step 3: Verify Build Success
After successful build, you should see:
```
BUILD SUCCESSFUL in Xs
```

## Installing the App

### Option 1: Install on Connected Device/Emulator
```bash
adb devices
adb install app\build\outputs\apk\debug\app-debug.apk
```

### Option 2: Transfer APK to Phone
1. Copy `app\build\outputs\apk\debug\app-debug.apk` to your Android phone
2. Enable "Install from Unknown Sources" in phone settings
3. Open the APK file on your phone to install

## Running the App

1. Open the **Pandal Finder** app on your Android device
2. Tap **"Find Nearby Pandals"** button
3. Grant location permissions when prompted
4. The app will display nearby pandals sorted by distance
5. Tap any pandal to open Google Maps navigation

## Testing Without Android Studio

### Using ADB Logcat (View Logs)
```bash
adb logcat -s PandalFinder:D
```

### Uninstall App
```bash
adb uninstall com.pandalfinder
```

### Reinstall After Changes
```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Common Issues

### Issue 1: Gradle build fails
**Solution**: Ensure JAVA_HOME is set correctly and Java is in PATH

### Issue 2: SDK not found
**Solution**: Set ANDROID_HOME environment variable to your Android SDK location

### Issue 3: ADB not recognized
**Solution**: Add Android platform-tools to your system PATH

### Issue 4: Location not detected
**Solution**: 
- Ensure GPS is enabled on the device
- Grant location permissions to the app
- Test on a real device (emulator GPS can be unreliable)

### Issue 5: Google Maps not opening
**Solution**: Ensure Google Maps app is installed on the device

## Customizing Pandal Locations

To add your own pandal locations, edit:
```
app\src\main\java\com\pandalfinder\Pandal.kt
```

Update the `getSamplePandals()` function with actual coordinates:
```kotlin
Pandal("Your Pandal Name", 22.5726, 88.3639),
```

## Clean Build

If you encounter issues, try a clean build:
```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
```

## Project Structure

```
PandalFinder/
├── app/
│   ├── build.gradle                 # App dependencies
│   └── src/main/
│       ├── AndroidManifest.xml      # Permissions & app config
│       ├── java/com/pandalfinder/
│       │   ├── MainActivity.kt      # Main screen logic
│       │   ├── Pandal.kt           # Pandal data class
│       │   └── PandalAdapter.kt    # RecyclerView adapter
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml # Main screen UI
│           │   └── item_pandal.xml  # List item UI
│           └── values/              # Strings, colors, themes
├── build.gradle                     # Project-level config
├── settings.gradle                  # Project settings
└── gradlew.bat                      # Gradle wrapper (Windows)
```

## Next Steps

1. Test the app on a real Android device for best results
2. Customize pandal locations with actual coordinates
3. Test navigation to each pandal location
4. Adjust the 3 km radius if needed (in `MainActivity.kt`)

Enjoy finding nearby pandals! 🎉
