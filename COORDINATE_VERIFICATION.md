# How to Verify Pandal Coordinates

## Why Coordinates Are Wrong

If Google Maps shows wrong location when you click a pandal, the coordinates in `Pandal.kt` are incorrect.

## How to Get Correct Coordinates

### Method 1: Using Google Maps (Recommended)

1. **Open Google Maps** on your computer or phone
2. **Search** for the pandal name (e.g., "Ahiritola Jubak Brinda, Kolkata")
3. **Right-click** on the exact location
4. **Click** the coordinates at the top (e.g., "22.5946391, 88.3576964")
5. Coordinates are now copied!

### Method 2: From URL

1. Open Google Maps
2. Search for pandal
3. Look at URL: `https://www.google.com/maps/@22.5946391,88.3576964,17z`
4. The numbers are: `latitude,longitude`

## Coordinate Format

### Correct Format for Kolkata
```kotlin
Pandal("Ahiritola Jubak Brinda", 22.5946391, 88.3576964)
//                                ^^^^^^^^^^  ^^^^^^^^^^
//                                Latitude    Longitude
```

### Valid Ranges for Kolkata
- **Latitude**: 22.4 to 22.7 (North)
- **Longitude**: 88.2 to 88.5 (East)

### Common Mistakes

❌ **Swapped coordinates**:
```kotlin
Pandal("Name", 88.3576964, 22.5946391)  // WRONG - longitude, latitude
```

✅ **Correct order**:
```kotlin
Pandal("Name", 22.5946391, 88.3576964)  // CORRECT - latitude, longitude
```

❌ **Wrong decimal places**:
```kotlin
Pandal("Name", 22.59, 88.35)  // Too few decimals, not accurate
```

✅ **Good accuracy** (6-7 decimals):
```kotlin
Pandal("Name", 22.5946391, 88.3576964)  // Accurate to ~11cm
```

## How to Fix Your Pandal.kt File

### Step 1: Identify Wrong Coordinates

Some coordinates in your file look suspicious:

**Lines 52-55** - All have the same coordinates:
```kotlin
Pandal("Karbagan Sarbojanin Durgotsab Committee", 22.5908559, 88.2164819),
Pandal("Kabiraj Bagan Sarbojonin Durgotsav", 22.5908559, 88.2164819),
Pandal("Mitali Sangha Durga Puja Pandal", 22.5908559, 88.2164819),
Pandal("Surir Bagan Sarbojanin Durgapuja", 22.5908559, 88.2164819),
```
This is likely incorrect - 4 different pandals can't be at the same location!

**Lines 114-116** - Same coordinates again:
```kotlin
Pandal("Bakul Bagan Sarbojanin Durgotsav", 22.5851266, 88.2883284),
Pandal("Kabiraj Bagan Sarbojonin Durgotsav", 22.5851266, 88.2883284),
Pandal("Singhi Park Sarbojanin Durga Puja Committee", 22.5851161, 88.2883283),
```

### Step 2: Verify Each Pandal

For each pandal in your list:

1. Google the pandal name + "Kolkata"
2. Find exact location on Google Maps
3. Copy coordinates
4. Replace in Pandal.kt:

```kotlin
// Before (wrong)
Pandal("Karbagan Sarbojanin Durgotsab Committee", 22.5908559, 88.2164819),

// After (correct - example, verify yourself)
Pandal("Karbagan Sarbojanin Durgotsab Committee", 22.5678123, 88.3456789),
```

### Step 3: Test in Google Maps

After updating:

1. Copy the new coordinates
2. Paste in Google Maps search: `22.5946391, 88.3576964`
3. Verify it shows the correct pandal location

## Quick Test Script

Test 5 random pandals:

1. **Ahiritola Jubak Brinda**: Search `22.5946391, 88.3576964` in maps
   - Should show: Ahiritola area
   
2. **Kumartuli Park**: Search `22.5989198, 88.3592198` in maps
   - Should show: Kumartuli area

3. **Hatibagan Sarbojonin**: Search `22.5951693, 88.3701795` in maps
   - Should show: Hatibagan area

If any DON'T match, those coordinates are wrong!

## Batch Update Guide

### If You Have a List

If you have an Excel/Google Sheets with pandal names and addresses:

1. Use [Google Geocoding Tool](https://www.google.com/maps)
2. Search each address
3. Copy coordinates
4. Update Pandal.kt line by line

### Using Python Script (Advanced)

You can create a script to geocode all addresses at once using Google Geocoding API, but this requires:
- Google Cloud account
- Geocoding API enabled
- API key

## After Fixing Coordinates

1. **Rebuild** the app:
   ```powershell
   .\gradlew.bat assembleDebug
   ```

2. **Reinstall**:
   ```powershell
   C:\Users\dibya\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
   ```

3. **Test** navigation to 5-10 pandals to verify they all open correct locations

## Summary

✅ **Always** use format: `Pandal("Name", latitude, longitude)`
✅ **Verify** coordinates by searching them in Google Maps
✅ **Test** at least 5 pandals after updating
✅ **Check** latitude is 22.x and longitude is 88.x for Kolkata
❌ **Never** swap latitude and longitude
❌ **Never** use same coordinates for multiple different pandals
