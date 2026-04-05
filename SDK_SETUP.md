# Android SDK Location Setup

The build needs to know where your Android SDK is installed.

## Option 1: Set ANDROID_HOME Environment Variable (Recommended)

### On Windows (Permanent):
1. Open System Properties → Advanced → Environment Variables
2. Add new System Variable:
   - Variable name: `ANDROID_HOME`
   - Variable value: `C:\Users\YourUsername\AppData\Local\Android\Sdk` (adjust to your actual path)
3. Restart VS Code/Terminal

### Common Android SDK Locations:
- **If you have Android Studio**: `C:\Users\<YourUsername>\AppData\Local\Android\Sdk`
- **Manual install**: Wherever you extracted the command-line tools

## Option 2: Create local.properties File (Quick Fix)

Create a file `local.properties` in the project root with:
```properties
sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

**IMPORTANT**: Use double backslashes (`\\`) in the path!

## How to Find Your Android SDK Location:

### If Android Studio is installed:
1. Open Android Studio
2. Go to File → Settings → Appearance & Behavior → System Settings → Android SDK
3. Copy the "Android SDK Location" path

### If using command-line tools only:
Look where you extracted the Android SDK command-line tools.

## Once you have the SDK path:

I can create the `local.properties` file for you. Just tell me your Android SDK path!

For example:
- `C:\Android\Sdk`
- `C:\Users\dibya\AppData\Local\Android\Sdk`
- Or wherever you installed it
