# Pandal Finder 📍

A simple Android app that automatically detects your location and shows nearby pandals within 3 km, built entirely in **VS Code** (no Android Studio required).

## Features ✨

- **Automatic Location Detection** - Uses FusedLocationProviderClient
- **Nearby Pandal Search** - Filters pandals within 3 km radius
- **Distance Calculation** - Shows exact distance to each pandal
- **One-Click Navigation** - Opens Google Maps for turn-by-turn directions
- **Simple UI** - Single screen with clean Material Design
- **No Backend Required** - All data is local and hardcoded

## Requirements 📋

- **Android Device**: Android 7.0 (API 24) or higher
- **Permissions**: Location access (requested at runtime)
- **Google Maps**: Required for navigation feature

## Quick Start 🚀

### For Users (Installing the APK)

1. Download `app-debug.apk` from the releases
2. Enable "Install from Unknown Sources" on your Android device
3. Install the APK
4. Open the app and grant location permissions
5. Tap "Find Nearby Pandals" to see nearby locations

### For Developers (Building from Source)

See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) for detailed build steps in VS Code.

**Quick Build:**
```powershell
cd d:\ANTI\PandalFinder
.\gradlew.bat assembleDebug
adb install app\build\outputs\apk\debug\app-debug.apk
```

## How It Works 🛠️

1. **Button Click** → Checks location permissions
2. **Permission Granted** → Gets current GPS location
3. **Calculate Distance** → Uses `Location.distanceTo()` for all pandals
4. **Filter & Sort** → Shows only pandals ≤ 3 km, sorted nearest first
5. **Display Results** → RecyclerView with name and distance
6. **Tap Pandal** → Opens Google Maps with `google.navigation:q=lat,lng`

## Code Structure 📁

```
app/src/main/
├── AndroidManifest.xml          # Permissions & app config
├── java/com/pandalfinder/
│   ├── MainActivity.kt          # Location detection & UI logic
│   ├── Pandal.kt               # Data class with sample pandals
│   └── PandalAdapter.kt        # RecyclerView adapter
└── res/
    ├── layout/
    │   ├── activity_main.xml   # Main screen layout
    │   └── item_pandal.xml     # Pandal list item
    └── values/                  # Strings, colors, themes
```

## Customization 🎨

### Adding Your Own Pandals

Edit `app/src/main/java/com/pandalfinder/Pandal.kt`:

```kotlin
fun getSamplePandals(): List<Pandal> {
    return listOf(
        Pandal("Your Pandal Name", 22.5726, 88.3639),
        // Add more pandals here
    )
}
```

### Changing Search Radius

Edit `MainActivity.kt` and change:
```kotlin
private const val MAX_DISTANCE_KM = 3.0f  // Change to your desired radius
```

## Tech Stack 🔧

- **Language**: Kotlin
- **Build System**: Gradle
- **IDE**: VS Code only (no Android Studio)
- **Location API**: Google Play Services - FusedLocationProviderClient
- **UI**: Material Design Components, RecyclerView
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 33 (Android 13)

## Permissions 🔐

The app requests:
- `ACCESS_FINE_LOCATION` - For precise GPS location
- `ACCESS_COARSE_LOCATION` - Fallback location
- `INTERNET` - Required by location services

All permissions are handled at runtime with proper user prompts.

## Known Limitations ⚠️

- Pandal data is hardcoded (no API integration)
- Requires Google Maps app for navigation
- GPS accuracy depends on device and environment
- Small sample size (10 pandals for demonstration)

## Future Enhancements 💡

Potential improvements (not currently implemented):
- Load pandal data from JSON file
- Add search/filter functionality
- Show pandals on a map view
- Add pandal photos and descriptions
- Save favorite pandals locally

## Troubleshooting 🐛

**Location not detected?**
- Ensure GPS is enabled
- Grant location permissions
- Test on a real device (emulator GPS is unreliable)

**Google Maps not opening?**
- Install Google Maps from Play Store
- Ensure it's set as default navigation app

**Build errors?**
- Check Java and Android SDK installation
- Set `ANDROID_HOME` environment variable
- See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md)

## License 📄

This is a demonstration project. Feel free to use and modify as needed.

## Contact & Support 💬

For issues or questions:
- Check [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md)
- Review the code comments
- Test on a real Android device for best results

---

**Built with VS Code | No Android Studio Required**
