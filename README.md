# 🛕 PandalFinder

### Your Real-Time Durga Puja Companion for Kolkata

> **Discover pandals. Find metro stations. Locate nearby toilets. Build
> your hopping route. Navigate with ease.**

PandalFinder is a modern Android application designed for **Durga Puja
pandal hopping across Kolkata and Howrah**.

It combines live GPS, interactive maps, Google routing, Google Places,
community-powered crowd/weather updates, metro discovery, and a
multi-stop hopping planner into one lightweight festival navigation
experience.

------------------------------------------------------------------------

## ✨ What is PandalFinder?

During Durga Puja, finding pandals is only part of the problem.

You also need to know:

-   📍 Where is the pandal?
-   🚶 How far is it?
-   🛵 How far is it by bike?
-   🚗 How far is it by car?
-   🌦️ What's the current weather?
-   👥 How crowded is it?
-   🚇 What's the nearest metro station?
-   🚻 Where is the nearest public toilet?
-   🗺️ How do I get there?
-   🛕 Which pandals should I visit next?

**PandalFinder brings all of this together in one map-first
experience.**

------------------------------------------------------------------------

# 🚀 Features

## 🗺️ Interactive Festival Map

Explore Kolkata and Howrah through an interactive map containing real
pandal locations.

Each place has its own marker type:

-   🛕 Pandals
-   🚇 Metro stations
-   🚻 Public toilets

The map remains the primary interface of the application.

------------------------------------------------------------------------

## 🔎 Universal Place Search

One compact search bar lets users search across multiple place
categories.

Search for:

``` text
Pandal names
Areas / localities
Metro stations
Public toilets
```

Example:

``` text
Khidderpore
Rabindra Sarobar
Howrah
Maidan
```

Search results identify the type of place before opening it.

------------------------------------------------------------------------

# 🎛️ Independent Map Filters

PandalFinder provides three independent map filters:

``` text
[PANDALS] [METRO] [TOILETS]
```

They can be combined freely.

### Examples

``` text
[PANDALS ✓] [METRO] [TOILETS]
```

Shows pandals only.

``` text
[PANDALS] [METRO ✓] [TOILETS]
```

Shows metro stations only.

``` text
[PANDALS] [METRO] [TOILETS ✓]
```

Shows public toilets only.

``` text
[PANDALS ✓] [METRO ✓] [TOILETS ✓]
```

Shows all three simultaneously.

------------------------------------------------------------------------

# 🛕 Pandal Discovery

Tap any pandal marker to open its detailed information.

The pandal detail view provides:

-   Pandal name
-   Area / location
-   Road distance
-   Walking distance
-   Two-wheeler distance
-   Driving distance
-   Current weather
-   Community crowd level
-   Nearest metro station
-   Add to Hopping
-   Google Maps navigation

------------------------------------------------------------------------

# 🚗 Google Routes Integration

PandalFinder uses Google's routing services for road-based distance
calculations.

``` text
Current GPS
     │
     ▼
Google Routes API
     │
     ├── 🚶 Walking
     ├── 🛵 Two-wheeler
     └── 🚗 Driving
```

The application does **not** use straight-line distance for road-route
information.

Example:

``` text
🚶 7.8 km
🛵 5.7 km
🚗 5.8 km
```

------------------------------------------------------------------------

# 🚇 Metro Discovery

Metro stations can be displayed independently or alongside pandals.

Tap a metro station to view:

-   Station name
-   Distance from current location
-   Add/remove from Hopping
-   Navigation

Example:

``` text
🚇 Rabindra Sarobar

1.8 km from you

[ + ]    [ Navigation ]
```

------------------------------------------------------------------------

# 🚻 Public Toilet Discovery

PandalFinder can discover nearby public toilets using **Google Places
API**.

Toilets have their own marker style and can be displayed independently
or alongside pandals and metro stations.

Tap a toilet to see:

-   Restroom name
-   Address
-   Current distance
-   Add/remove from Hopping
-   Google Maps navigation

Example:

``` text
🚻 Public Toilet

Maidan, Kolkata

850 m from you

[ + ]    [ Navigation ]
```

------------------------------------------------------------------------

# 🛣️ Hopping --- Build Your Own Route

The **Hopping** section lets users create a personalized multi-stop
festival itinerary.

A route can contain:

``` text
1. 🛕 Khidderpore 75 Pally
2. 🚇 Rabindra Sarobar
3. 🚻 Pay & Use Restroom
4. 🛕 Another Pandal
```

Users can:

-   Add places
-   Remove places
-   Reorder stops
-   Prevent duplicate stops
-   Calculate route information
-   Start the route through Google Maps

The itinerary is persisted across app restarts.

------------------------------------------------------------------------

## 🧭 Mixed-Stop Routing

PandalFinder isn't limited to pandal-only routes.

``` text
Current Location
       │
       ▼
🛕 Pandal
       │
       ▼
🚇 Metro Station
       │
       ▼
🛕 Pandal
       │
       ▼
🚻 Restroom
       │
       ▼
🛕 Final Pandal
```

Every stop retains its:

-   Name
-   Type
-   Coordinates
-   Address/details

------------------------------------------------------------------------

# 🌦️ Community Weather

Users near a pandal can report the current rain condition:

``` text
☀️ No rain
🌦️ Drizzle
🌧️ Raining
⛈️ Heavy rain
```

Reports are stored in Firebase and expire after a limited period so
outdated conditions don't remain indefinitely.

Only users who are physically near a pandal or have recently visited it
can submit local updates.

> **Keep contributing to keep PandalFinder updated.**

------------------------------------------------------------------------

# 👥 Community Crowd Reports

Visitors can report crowd intensity using:

``` text
1 ───────────── 10
```

    Level Meaning
  ------- ----------------
     1--2 Empty
     3--4 Light
     5--6 Moderate
     7--8 Very busy
        9 Extremely busy
       10 Packed

The scale represents **crowd intensity**, not an exact number of people.

Only nearby/recent visitors can contribute.

------------------------------------------------------------------------

# 📍 Live Location

PandalFinder uses the device's current GPS position for:

-   Nearby discovery
-   Distance calculations
-   Route origins
-   Metro distance
-   Toilet distance
-   Contribution eligibility
-   Navigation

------------------------------------------------------------------------

# 🗺️ Navigation

Every supported destination can be handed off directly to Google Maps.

``` text
Pandal / Metro / Toilet
          │
          ▼
     Navigation
          │
          ▼
      Google Maps
          │
          ▼
 Turn-by-turn directions
```

------------------------------------------------------------------------

# 🎨 Design

PandalFinder uses a visual identity inspired by **Durga Puja and
Kolkata**.

### Core palette

  Color             Hex
  ----------------- -----------
  Sindoor Red       `#E52B00`
  Cream             `#FFF5E3`
  Orange            `#F97E04`
  Gold              `#FBC222`
  Festival Yellow   `#FBEF00`
  Green             `#45A701`

The interface focuses on:

-   Warm festival colors
-   Modern Android design
-   Readable typography
-   Floating map controls
-   Rounded surfaces
-   Strong visual hierarchy
-   Minimal navigation
-   Outdoor usability

------------------------------------------------------------------------

# 🧭 App Structure

``` text
                    PANDALFINDER
                         │
                         ▼
                 ┌───────────────┐
                 │      MAP      │
                 └───────┬───────┘
                         │
       ┌─────────────────┼─────────────────┐
       ▼                 ▼                 ▼
   🛕 PANDALS        🚇 METRO          🚻 TOILETS
       │                 │                 │
       └─────────────────┼─────────────────┘
                         ▼
                    PLACE CARD
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
        + HOPPING              NAVIGATION
              │                     │
              ▼                     ▼
        HOPPING PLAN           GOOGLE MAPS
              │
              ▼
        MULTI-STOP ROUTE
```

------------------------------------------------------------------------

# 🧰 Tech Stack

### Android

-   **Kotlin**
-   **Android SDK**
-   **Gradle**
-   **Google Play Services Location**
-   **Firebase**
-   **Firestore**

### Maps & Location

-   Interactive map rendering
-   Device GPS
-   Google Routes API
-   Google Places API
-   Google Maps navigation

------------------------------------------------------------------------

# 🔥 Firebase

Firebase / Firestore is used for community-generated information such
as:

``` text
Crowd Reports
Weather Reports
```

The app does not require traditional user accounts for its core
experience.

------------------------------------------------------------------------

# ⚙️ Setup

## Requirements

-   Android SDK
-   JDK
-   Gradle wrapper
-   Android device or emulator
-   USB debugging for physical-device testing
-   Google Cloud project
-   Firebase project

------------------------------------------------------------------------

## 🔑 Google Maps Platform

The application uses:

``` text
Maps
Routes API
Places API
```

Required APIs must be enabled in the Google Cloud project.

**Never commit API keys to GitHub.**

Use local configuration such as:

``` text
local.properties
```

or the project's secure build configuration.

------------------------------------------------------------------------

# 📱 Running the App

### 1. Configure Android SDK

Set the SDK location in:

``` text
local.properties
```

Example:

``` properties
sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

### 2. Enable USB Debugging

Enable:

``` text
Settings
→ Developer Options
→ USB Debugging
```

Then connect the device and verify:

``` bash
adb devices
```

### 3. Build

Windows:

``` powershell
.\gradlew.bat assembleDebug
```

macOS / Linux:

``` bash
./gradlew assembleDebug
```

### 4. Install

``` bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

------------------------------------------------------------------------

# 🧪 Development Workflow

``` text
VS Code
    ↓
Gradle CLI
    ↓
Debug APK
    ↓
ADB / USB Debugging
    ↓
Physical Android Device
```

------------------------------------------------------------------------

# 🛠️ Useful Commands

### Clean

``` powershell
.\gradlew.bat clean
```

### Build Debug APK

``` powershell
.\gradlew.bat assembleDebug
```

### Install

``` powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Check Connected Devices

``` powershell
adb devices
```

### Launch

``` powershell
adb shell am start -n com.pandalfinder/.MainActivity
```

### View Logs

``` powershell
adb logcat
```

------------------------------------------------------------------------

# 🧩 Troubleshooting

### Location isn't updating

Check:

-   GPS is enabled
-   Location permission is granted
-   Google Play Services is available
-   Device location settings are enabled

### Google Maps doesn't open

Make sure Google Maps or another supported mapping application is
installed.

### Routes are unavailable

Check:

-   Routes API is enabled
-   API key is valid
-   API key restrictions allow the required service
-   Billing is configured
-   Device has internet access

### Toilets aren't appearing

Check:

-   Places API (New) is enabled
-   API key allows Places requests
-   Required Places endpoint is available
-   Device has internet access

### Firebase updates aren't working

Check:

-   Firebase configuration is present
-   Firestore is enabled
-   Firestore rules permit the intended operation
-   Device has internet access
-   Logcat for Firebase exceptions

------------------------------------------------------------------------

# 🗺️ Data Philosophy

PandalFinder is designed around **real location data instead of
fabricated values**.

The application avoids:

-   Fake route distances
-   Fake toilet locations
-   Fake crowd values
-   Hardcoded current GPS positions
-   Map-center coordinates used as destinations
-   Artificial navigation routes

When a service cannot provide reliable information, the application
should report that state instead of pretending that a value is accurate.

------------------------------------------------------------------------

# 🛡️ Privacy

PandalFinder is designed to work without traditional account creation.

Location is primarily used for:

``` text
Nearby discovery
Routing
Navigation
Contribution eligibility
Distance calculations
```

Community contributions use only the information necessary to associate
a report with a place and validate/display community updates.

------------------------------------------------------------------------

# 🗺️ Roadmap

### ✅ Completed

-   [x] Live GPS location
-   [x] Interactive pandal map
-   [x] Pandal discovery
-   [x] Pandal search
-   [x] Metro discovery
-   [x] Universal place search
-   [x] Independent pandal/metro/toilet filters
-   [x] Google Routes integration
-   [x] Walking route distance
-   [x] Two-wheeler route distance
-   [x] Driving route distance
-   [x] Google Maps navigation
-   [x] Automatic weather
-   [x] Community weather reports
-   [x] Community crowd reports
-   [x] Nearby/recent visitor contribution validation
-   [x] Firebase Firestore integration
-   [x] Google Places toilet discovery
-   [x] Toilet detail cards
-   [x] Metro detail cards
-   [x] Hopping itinerary
-   [x] Mixed pandal/metro/toilet routes
-   [x] Stop reordering
-   [x] Duplicate-stop prevention
-   [x] Persistent hopping plan
-   [x] Durga Puja visual identity

### 🚧 Future Ideas

-   [ ] Better route optimization
-   [ ] Festival/event timing information
-   [ ] More detailed accessibility information
-   [ ] Pandal photography
-   [ ] Offline map support
-   [ ] More community-generated festival information

------------------------------------------------------------------------

# 🤝 Contributing

Contributions are welcome.

``` bash
git checkout -b feature/your-feature
```

Make your changes, test them on a physical Android device, then:

``` bash
git add .
git commit -m "Add your feature"
git push origin feature/your-feature
```

Then open a pull request.

------------------------------------------------------------------------

# 📜 Credits

Built with:

-   Kotlin
-   Android
-   Google Play Services
-   Google Maps Platform
-   Google Routes API
-   Google Places API
-   Firebase
-   Firestore
-   OpenStreetMap / map data where applicable

------------------------------------------------------------------------

# 🛕 PandalFinder

### Discover Kolkata. Build your route. Experience Durga Puja.

**Made for pandal hoppers.**
