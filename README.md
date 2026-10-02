# 🛕 PandalQuest

### Your Real-Time Durga Puja Companion for Kolkata

> **Discover pandals. Find metro stations. Locate nearby toilets. Build
> your hopping route. Track crowd updates. Share photos. Navigate with
> ease.**

**PandalQuest v2.1.0** is a modern Android application built for Durga
Puja pandal hopping across Kolkata and Howrah.

It combines live GPS, an interactive festival map, multi-provider road
routing (Google Routes + OpenRouteService fallback), Google Maps navigation,
Firebase-powered community updates, Supabase photo sharing, Google Places public toilet
discovery, metro discovery, personal saved/visited collections, and a smarter
multi-stop hopping planner into one map-first festival experience.

------------------------------------------------------------------------

# 🚀 What’s New in v2.1.0 — Smarter Routes & Nearby Toilets

Version 2.1.0 brings major intelligence improvements to itinerary planning,
live toilet discovery via Google Places, and resilience to road-route calculations:

### 🧠 Smarter Auto Plan & Metro Decisions

- **Complete Journey Time Comparison:** The Auto Plan now computes the full
  journey time ($Walk + Metro + Wait/Transfer + Walk$) vs. direct road/walking time.
- **5-Minute Minimum Time Saving Threshold:** Metro transit is only suggested
  when it saves at least 5 minutes over the direct route.
- **Detour Protection:** Rejects unreasonable walking detours, backtracking,
  or unconnected lines.
- **Pure Itinerary Stops:** Metro stations are treated as transport legs,
  never polluted as standalone pandal hopping stops.

### 🛣️ OpenRouteService (HeiGIT) Road-Routing Fallback

- **Zero Downtime Routing:** When Google Routes is unavailable or unbilled,
  the app seamlessly falls back to OpenRouteService directions (`https://api.heigit.org`).
- **Real Route Distances:** Authentic driving, walking, and cycling route
  distances (never fabricated Haversine estimates).
- **In-Memory Caching & Rate-Limit Protection:** Smart 5-minute caching and
  cooldown backoff.

------------------------------------------------------------------------

# 🚀 Core Features

## 🗺️ Interactive Festival Map

Explore Kolkata and Howrah through an interactive map containing real
pandal locations.

Supported place types include:

- Pandals
- Metro stations
- Public toilets
- Festival zones where reliable data is available

The map remains the primary interface.

## 🔎 Universal Place Search

Search across supported categories:

``` text
Pandal names
Areas / localities
Metro stations
Public toilets
```

## 🎛️ Independent Map Filters

``` text
[PANDALS] [METRO] [TOILETS]
```

Filters can be combined freely, including showing all three
simultaneously.

## 🛕 Pandal Discovery

A pandal detail view can provide:

- Pandal name
- Area / location
- Road distance
- Walking distance
- Two-wheeler distance
- Driving distance
- Current weather
- Community crowd level
- Nearest metro station
- Community photos
- Saved status
- Visited status
- Add to Hopping
- Google Maps navigation

------------------------------------------------------------------------

# 📸 Community Pandal Photos

Users can add photographs from the device gallery or camera.

Upload flow:

``` text
Pandal Detail
     ↓
Add Photo
     ↓
Camera / Gallery
     ↓
Preview
     ↓
Upload
     ↓
Firebase Cloud Storage
     ↓
Firestore metadata
     ↓
Community Gallery
```

Firestore stores metadata such as:

``` text
pandalId
storagePath
downloadUrl
uploadedBy
createdAt
status
```

Image binary data should remain in Cloud Storage rather than being
stored directly in Firestore.

Uploads should use appropriate file-size, content-type and Firebase
Security Rules.

------------------------------------------------------------------------

# ❤️ My Pandal

The bottom navigation is intentionally minimal:

``` text
[ Map ] [ Hopping ] [ My Pandal ]
```

### Saved

Save pandals for later. Saved state persists across app restarts.

### Pandal Passport

Track pandals you have visited and your festival progress.

------------------------------------------------------------------------

# 🛣️ Hopping

Create a personalized multi-stop itinerary containing pandals, metro
stations and toilets.

Example:

``` text
1. Pandal
2. Metro Station
3. Public Restroom
4. Pandal
```

Users can:

- Add places
- Remove places
- Reorder stops
- Prevent duplicate stops
- Calculate route information
- Start navigation through Google Maps

The itinerary persists across app restarts.

------------------------------------------------------------------------

# 🚗 Multi-Provider Road Routing (Google + OpenRouteService)

The intended route flow uses a resilient fallback pipeline:

``` text
Current Device GPS
        │
        ▼
Primary: Google Routes API (ComputeRoutes REST)
        │
        ├── Success ─────────► Return Google Route Result
        │
        └── Failure/Unbilled ─► Fallback: OpenRouteService (HeiGIT)
                                    │
                                    ├── Success ──► Return ORS Route Result
                                    │
                                    └── Failure ──► Display "Route unavailable"
```

### Supported Profiles:
- **Driving / Car:** `RouteProfile.DRIVING` (Google: `DRIVE` / ORS: `driving-car`)
- **Walking:** `RouteProfile.WALKING` (Google: `WALK` / ORS: `foot-walking`)
- **Two-Wheeler / Cycling:** `RouteProfile.CYCLING` (Google: `TWO_WHEELER` / ORS: `cycling-regular`)

### Configuration (`secrets.properties`):
```properties
# Primary: Google Routes API Key
ROUTES_API_KEY=YOUR_GOOGLE_ROUTES_KEY_HERE

# Fallback: OpenRouteService Key (HeiGIT)
ORS_API_KEY=YOUR_OPENROUTESERVICE_KEY_HERE
```

> **Note on OpenRouteService API Host:**
> OpenRouteService requests use the active HeiGIT endpoint:
> `https://api.heigit.org/openrouteservice/v2/directions/{profile}`
> The legacy `api.openrouteservice.org` is deprecated.
> All coordinate requests follow the GeoJSON specification: `[longitude, latitude]`.

Road-route information is never replaced with fabricated or straight-line
Haversine values. If both providers fail or are unconfigured, the UI clearly
displays `"Route unavailable"`.

------------------------------------------------------------------------

# 🚇 Metro Discovery

Tap a metro station to view:

- Station name
- Distance from current location
- Add/remove from Hopping
- Navigation

## 🚻 Public Toilet Discovery

Toilets are discovered using Google Places services.

Tap a toilet to view:

- Restroom name
- Address
- Current distance
- Add/remove from Hopping
- Navigation

Toilet locations should come from real place data rather than fabricated
coordinates.

------------------------------------------------------------------------

# 👥 Community Crowd Reports

Visitors can report crowd intensity on a 1–10 scale.

| Level | Meaning        |
|-------|----------------|
| 1–2   | Empty          |
| 3–4   | Light          |
| 5–6   | Moderate       |
| 7–8   | Very busy      |
| 9     | Extremely busy |
| 10    | Packed         |

The scale represents crowd intensity, not an exact number of people.

Recent reports are stored through Firebase and can be synchronized to
other users. Where contribution validation is enabled, only
nearby/recent visitors can submit reports.

> **Keep contributing to keep PandalQuest updated.**

------------------------------------------------------------------------

# 🌦️ Weather

PandalQuest supports automatic weather information and community/local
observations where implemented.

Examples include:

``` text
No rain
Drizzle
Raining
Heavy rain
```

Automatic weather should come from the configured weather source.
Community observations should represent recent local conditions and
become stale after the configured validity period.

------------------------------------------------------------------------

# 📍 Live Location

Device GPS is used for:

- Nearby discovery
- Distance calculations
- Route origins
- Metro distance
- Toilet distance
- Contribution eligibility
- Navigation

------------------------------------------------------------------------

# 🗺️ Navigation

Supported destinations can be handed off to Google Maps:

``` text
Pandal / Metro / Toilet
          ↓
      Navigation
          ↓
      Google Maps
```

------------------------------------------------------------------------

# 🎨 Design

PandalQuest uses a visual identity inspired by Durga Puja and Kolkata.

| Color           | Hex       |
|-----------------|-----------|
| Sindoor Red     | `#E52B00` |
| Cream           | `#FFF5E3` |
| Orange          | `#F97E04` |
| Gold            | `#FBC222` |
| Festival Yellow | `#FBEF00` |
| Green           | `#45A701` |

The UI focuses on warm festival colors, modern Android design, readable
typography, floating map controls, rounded surfaces, strong visual
hierarchy and minimal navigation.

------------------------------------------------------------------------

# 🧭 App Structure

``` text
                       PANDALFINDER
                            │
                            ▼
                           MAP
                            │
             ┌──────────────┼──────────────┐
             ▼              ▼              ▼
          PANDALS         METRO          TOILETS
             │              │              │
             └──────────────┼──────────────┘
                            ▼
                       PLACE CARD
                       /                               ▼           ▼
                  HOPPING     NAVIGATION
                     │             │
                     ▼             ▼
               MULTI-STOP      GOOGLE MAPS
                   PLAN
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
      MY PANDAL             COMMUNITY
      /       \              /         \
   SAVED    PASSPORT      CROWD      PHOTOS
```

------------------------------------------------------------------------

# 🧰 Tech Stack

### Android

- Kotlin
- Android SDK
- Gradle
- Google Play Services Location
- Firebase
- Cloud Firestore
- Firebase Cloud Storage

### Maps & Location

- Google Maps Platform
- Google Routes API
- Google Places API / Places API (New)
- Google Maps navigation
- Device GPS / location services

### Community Data

- Firebase Firestore
- Firebase Cloud Storage
- Realtime listeners where required

------------------------------------------------------------------------

# 🔥 Firebase Architecture

Firebase is used for shared community information:

``` text
Firestore
├── Crowd Reports
├── Weather / community observations
├── Pandal photo metadata
└── Other shared community data

Cloud Storage
└── Pandal photographs
```

The core experience is designed without requiring traditional account
creation.

Production Firebase Security Rules should restrict writes and prevent
users from modifying other users’ content.

------------------------------------------------------------------------

# ⚙️ Setup

## Requirements

- Android SDK
- JDK
- Gradle wrapper
- Android device or emulator
- USB debugging for physical-device testing
- Google Cloud project
- Firebase project

## Google Maps Platform

The application uses:

``` text
Maps SDK for Android
Routes API
Places API (New)
```

Required APIs must be enabled in the Google Cloud project.

Configure API-key restrictions according to the actual Android and
web-service request architecture.

**Never commit API keys to GitHub.**

Use secure local configuration such as:

``` text
local.properties
```

## Firebase

Configure the Firebase Android project and enable the required services:

``` text
Cloud Firestore
Cloud Storage
```

Configure Firebase Security Rules before production use.

------------------------------------------------------------------------

# 📱 Running the App

### Configure Android SDK

Set the SDK location in `local.properties`:

``` properties
sdk.dir=C:\Users\YourUsername\AppData\Local\Android\Sdk
```

### Enable USB Debugging

``` text
Settings
→ Developer Options
→ USB Debugging
```

Verify the device:

``` bash
adb devices
```

### Build

Windows:

``` powershell
.\gradlew.bat assembleDebug
```

macOS / Linux:

``` bash
./gradlew assembleDebug
```

### Install

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

Real-device testing is recommended for GPS, Maps, Routes, Places,
camera/photo picker, Firebase synchronization and Google Maps
navigation.

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

### Location isn’t updating

Check:

- GPS is enabled
- Location permission is granted
- Google Play Services is available
- Device location settings are enabled

### Google Maps doesn’t open

Make sure Google Maps or another supported mapping application is
installed and the navigation destination is valid.

### Routes are unavailable

Check:

- Routes API is enabled
- API key is valid
- Billing is configured where required
- API-key restrictions match the actual request architecture
- Origin coordinates are valid
- Destination coordinates are valid
- Device has internet access
- Routes response parsing is correct

Use Logcat to inspect the actual Google Routes error. Never replace a
failed route with a fabricated distance.

### Toilets aren’t appearing

Check:

- Places API (New) is enabled
- API key allows the required Places functionality
- Current Places implementation matches the enabled API
- Location permission is available where required
- Device has internet access

### Firebase updates aren’t working

Check:

- Firebase configuration is present
- Firestore is enabled
- Cloud Storage is enabled
- Firebase Security Rules permit the intended operation
- Realtime listeners are attached correctly
- Device has internet access
- Logcat for Firebase exceptions

### Community photos aren’t appearing

Check:

- Storage upload completed
- Firestore metadata write completed
- Storage path/download URL is valid
- Firestore listener is active
- Storage and Firestore rules allow the intended read
- The image can be loaded by the device

------------------------------------------------------------------------

# 🗺️ Data Philosophy

PandalQuest is designed around **real location and community data
instead of fabricated values**.

The application avoids:

- Fake route distances
- Fake toilet locations
- Fake crowd values
- Hardcoded current GPS positions
- Map-center coordinates used as destinations
- Artificial navigation routes
- Fake community reports

When a service cannot provide reliable information, the application
should report that state instead of pretending that a value is accurate.

------------------------------------------------------------------------

# 🛡️ Privacy

PandalQuest is designed to work without traditional account creation
for its core experience.

Location is primarily used for:

``` text
Nearby discovery
Routing
Navigation
Contribution eligibility
Distance calculations
```

Community contributions should use only the information necessary to
associate a contribution with a place and validate/display the
contribution.

Uploaded photographs should use appropriate Firebase Storage and
Firestore security controls.

------------------------------------------------------------------------

# 🗺️ Roadmap

## ✅ Completed / v2.1.0

- [x] Live GPS location
- [x] Interactive pandal map
- [x] Pandal discovery
- [x] Pandal search
- [x] Metro discovery
- [x] Universal place search
- [x] Independent pandal/metro/toilet filters
- [x] Multi-provider road routing (Google Routes + OpenRouteService)
- [x] HeiGIT OpenRouteService fallback integration
- [x] Smarter Auto Plan with total journey time evaluation & 5-min threshold
- [x] Walking route distance
- [x] Two-wheeler / cycling route distance
- [x] Driving route distance
- [x] Google Maps navigation
- [x] Automatic weather
- [x] Community weather reports
- [x] Community crowd reports
- [x] Nearby/recent visitor contribution validation
- [x] Firebase Firestore integration
- [x] Google Places toilet discovery
- [x] Toilet detail cards
- [x] Metro detail cards
- [x] Hopping itinerary
- [x] Mixed pandal/metro/toilet routes
- [x] Stop reordering
- [x] Duplicate-stop prevention
- [x] Persistent hopping plan
- [x] Saved pandals
- [x] Pandal Passport
- [x] My Pandal section
- [x] Community pandal photos (multi-select + fullscreen viewer)
- [x] Supabase Storage photo integration
- [x] Realtime community data architecture
- [x] Durga Puja visual identity (Liquid Glass system)
- [x] v2.1.0 release

## 🚧 Future Ideas

- [ ] Festival/event timing information
- [ ] More detailed accessibility information
- [ ] Offline map/core data support
- [ ] More community-generated festival information
- [ ] Better photo moderation
- [ ] Richer pandal information and verified details

------------------------------------------------------------------------

# 🤝 Contributing

Contributions are welcome.

``` bash
git checkout -b feature/your-feature
```

Make changes and test them on a physical Android device.

``` bash
git add .
git commit -m "Add your feature"
git push origin feature/your-feature
```

Then open a pull request with a clear description of the change.

------------------------------------------------------------------------

# 📜 Credits

Built with:

- Kotlin
- Android
- Google Play Services
- Google Maps Platform
- Google Routes API
- OpenRouteService / HeiGIT API
- Google Places API
- Firebase
- Cloud Firestore
- Supabase Storage
- OpenStreetMap / MapLibre GL where applicable

------------------------------------------------------------------------

# 🛕 PandalQuest

### Discover Kolkata. Build your route. Experience Durga Puja.

**Made for pandal hoppers.**

**Current release: v2.1.0**
