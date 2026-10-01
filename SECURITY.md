# PandalFinder Security Architecture & Policy

Version: 2.0.0  
Last Security Audit: September 2026

---

## 1. System Architecture

PandalFinder combines Firebase, Supabase, and Google Maps Platform services into a zero-login, privacy-first Android client application:

```
┌─────────────────────────────────────────────────────────────┐
│                    Android Application                      │
│                  (com.pandalfinder)                         │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
       (Anonymous Auth)            (Bearer JWT / API Key)
               │                              │
               ▼                              ▼
 ┌───────────────────────────┐  ┌───────────────────────────┐
 │    Firebase Ecosystem     │  │     Supabase Storage      │
 │  - Anonymous & Email Auth │  │  - Bucket: pandal-photos  │
 │  - Cloud Firestore        │  │  - Third-Party Auth RLS   │
 │  - Security Rules Engine  │  │  - Size Limit: 5 MB       │
 └───────────────────────────┘  └───────────────────────────┘
               │
               ▼
 ┌───────────────────────────┐
 │   Google Maps Platform    │
 │  - Google Routes API      │
 │  - Google Places API      │
 │  (Package & SHA-1 Locked) │
 └───────────────────────────┘
```

---

## 2. Normal User Authentication

- **Zero-Friction Anonymous Auth:** Normal users are authenticated silently in the background using `FirebaseAuth.getInstance().signInAnonymously()`.
- **No Client Account Barrier:** Users never encounter registration forms, passwords, or mandatory onboarding accounts.
- **Cryptographic User Identity:** Every contribution (photos, crowd updates, favorites) is anchored to the Firebase `request.auth.uid`. Client devices cannot spoof or forge user IDs.
- **No Token Leaks:** ID tokens are handled in-memory and are never written to `SharedPreferences` or printed in logs.

---

## 3. Admin Authentication

- **Separate Authentication Flow:** Administrators authenticate via standard Firebase Email & Password authentication.
- **No Hardcoded Credentials:** Neither admin emails, passwords, hashes, nor master keys exist anywhere in the source code, `BuildConfig`, assets, or resources.
- **Generic Failure Feedback:** Sign-in failure responses use generic access-denied messaging (`"Admin access denied."`), preventing email existence enumeration attacks.
- **Automatic Fallback:** If an authenticated email user is not on the server-side admin allowlist, the session is revoked immediately and the client is automatically switched back to Anonymous Auth.

---

## 4. Admin Authorization (Server-Side Allowlist)

Because the project runs on Firebase Spark without requiring Cloud Functions:

- **Server-Side Trust Anchor:** Admin privileges are strictly derived from the Firestore collection `/admins/{firebaseUid}`.
  ```json
  {
    "role": "admin",
    "enabled": true
  }
  ```
- **Client Write Prohibition:** The `/admins` collection has `allow write: if false;` in Firestore Security Rules. No mobile client (anonymous, regular, or compromised) can create, update, delete, or promote an admin.
- **Zero Client Trust:** Client-side flags (`isAdmin`, `role`, Intent extras, SharedPreferences) are never trusted for authorization.

---

## 5. Photo Ownership Model

Every community photo has exactly **one owner**: the Firebase UID of the uploading user (`uploadedBy = request.auth.uid`).

| Role | View Photo | Upload Photo | Replace Own | Delete Own | Edit/Delete Others |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Owner (User A)** | ✅ Allow | ✅ Allow | ✅ Allow | ✅ Allow | ❌ Denied |
| **Other User (User B)** | ✅ Allow | ✅ Allow | ❌ Denied | ❌ Denied | ❌ Denied |
| **Authorized Admin** | ✅ Allow | ✅ Allow | ✅ Allow | ✅ Allow | ✅ Allow |

### Ownership Immutability
- Photo ownership cannot be transferred. `request.resource.data.uploadedBy == resource.data.uploadedBy` is enforced by Firestore rules on every update.

---

## 6. Firestore Security Rules

Full rules are defined in [`firestore.rules`](file:///Volumes/d/Projects/Ready/PandalFinder/firestore.rules). Key enforcement highlights:

```firestore
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    function isAdmin() {
      return request.auth != null
        && exists(/databases/$(database)/documents/admins/$(request.auth.uid))
        && get(/databases/$(database)/documents/admins/$(request.auth.uid)).data.role == 'admin'
        && get(/databases/$(database)/documents/admins/$(request.auth.uid)).data.enabled == true;
    }

    match /admins/{adminId} {
      allow read: if request.auth != null && (request.auth.uid == adminId || isAdmin());
      allow write: if false;
    }

    match /pandalPhotos/{photoId} {
      allow read: if true;
      allow create: if request.auth != null
                    && request.resource.data.uploadedBy == request.auth.uid
                    && request.resource.data.status in ['active', 'hidden', 'flagged']
                    && request.resource.data.storagePath is string
                    && request.resource.data.downloadUrl.size() > 0
                    && request.resource.data.pandalId.size() > 0;
      allow update: if request.auth != null && (
                      (resource.data.uploadedBy == request.auth.uid
                       && request.resource.data.uploadedBy == resource.data.uploadedBy
                       && request.resource.data.pandalId == resource.data.pandalId
                       && request.resource.data.id == resource.data.id)
                      || isAdmin()
                    );
      allow delete: if (request.auth != null && resource.data.uploadedBy == request.auth.uid) || isAdmin();
    }

    match /crowdReports/{reportId} {
      allow read: if true;
      allow create: if request.auth != null
                    && request.resource.data.userId == request.auth.uid
                    && request.resource.data.pandalId.size() > 0
                    && ((request.resource.data.level is int && request.resource.data.level >= 1 && request.resource.data.level <= 10)
                        || (request.resource.data.crowdLevel is int && request.resource.data.crowdLevel >= 1 && request.resource.data.crowdLevel <= 10));
      allow update, delete: if isAdmin();
    }

    match /weatherReports/{reportId} {
      allow read: if true;
      allow create: if request.auth != null
                    && request.resource.data.userId == request.auth.uid
                    && request.resource.data.pandalId.size() > 0
                    && (request.resource.data.condition in ['NO_RAIN', 'DRIZZLE', 'RAINING', 'HEAVY_RAIN']
                        || request.resource.data.weatherCondition in ['NO_RAIN', 'DRIZZLE', 'RAINING', 'HEAVY_RAIN']);
      allow update, delete: if isAdmin();
    }

    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

---

## 7. Supabase Storage Security (Row Level Security)

Defined in [`supabase_security_setup.sql`](file:///Volumes/d/Projects/Ready/PandalFinder/supabase_security_setup.sql):

- **Object Path Hierarchy:** `pandal-photos/{firebaseUid}/{pandalId}/{photoId}.jpg`
- **SELECT Policy:** Public read access on bucket `pandal-photos`.
- **INSERT Policy:** Authenticated user where `(storage.foldername(name))[1] = auth.jwt() ->> 'sub'` OR `is_admin()`.
- **UPDATE / DELETE Policy:** Owner where `(storage.foldername(name))[1] = auth.jwt() ->> 'sub'` OR `is_admin()`.
- **Public Write Prohibition:** No public or unauthenticated write/update/delete policies exist.

---

## 8. Crowd 500m Proximity Rule & Security Limitations

- **Client-Side Verification:** When tapping "Update Crowd", Android calculates GPS distance via `Location.distanceTo`. If distance $> 500\text{ m}$, the 1–10 selector does not open.
- **Architectural Limitation & Transparency:** In a serverless/Spark architecture without Cloud Functions, client-side GPS coordinates can theoretically be modified on rooted/mock-location devices. The backend strictly enforces valid UID authentication, valid pandal references, valid integer crowd ratings (1–10), and server timestamps, preventing injection of malicious types or bulk corruption.

---

## 9. Google API Key Handling

- **Key Restriction:** The `ROUTES_API_KEY` is restricted in Google Cloud Console to:
  1. Google Routes API
  2. Google Places API (New)
  3. Android Application Package Name: `com.pandalfinder`
  4. Certificate SHA-1: `4F4C1806D054E172BF09FB0760599707D9BCFB5E`
- **Request Headers:** All Google REST requests send `X-Android-Package` and `X-Android-Cert` headers.

---

## 10. Secrets Policy

- **No Secrets in Source:** `secrets.properties` and `local.properties` are `.gitignore`d.
- **Client Artifact Safety:** The Android APK only contains client-facing publishable keys (`SUPABASE_PUBLISHABLE_KEY`, `GOOGLE_ROUTES_API_KEY`).
- **Zero Server-Role Keys:** Supabase `service_role` keys, Firebase service accounts, and database passwords are NEVER bundled in the APK.

---

## 11. Privacy & Location Handling

- **Ephemeral Use:** Location is requested solely for on-device sorting, routing, nearest metro calculations, and the 500m crowd eligibility check.
- **Zero Continuous Tracking:** PandalFinder does not run background location listeners or log continuous telemetry to external servers.
- **Photo Upload Privacy:** Community photo uploads have zero location prerequisites and do not require location permissions.

---

## 12. Manual Admin Bootstrap Setup

To grant administrative access to a trusted operator:

### A. Firebase Console
1. Open **Firebase Console $\rightarrow$ Authentication $\rightarrow$ Users**.
2. Click **Add User**, provide the admin email and a strong password.
3. Copy the generated **Firebase User UID** (e.g. `pX9qZ...`).
4. Open **Cloud Firestore $\rightarrow$ Data**.
5. Add a document in collection `admins` with Document ID = `<ADMIN_FIREBASE_UID>`:
   ```json
   {
     "role": "admin",
     "enabled": true,
     "createdAt": 1727670000000
   }
   ```

### B. Supabase Dashboard (Optional if Supabase Moderation is enabled)
1. Open **Supabase SQL Editor**.
2. Execute:
   ```sql
   INSERT INTO public.admin_users (firebase_uid, role, enabled)
   VALUES ('<ADMIN_FIREBASE_UID>', 'admin', true)
   ON CONFLICT (firebase_uid) DO UPDATE SET enabled = true, role = 'admin';
   ```

---

## 13. Security Verification Tests

Automated tests in [`AdminAndSecurityTest.kt`](file:///Volumes/d/Projects/Ready/PandalFinder/app/src/test/java/com/pandalfinder/AdminAndSecurityTest.kt):

- `testPhotoOwnershipPermissions`: Validates owner can delete, foreign user is rejected.
- `testPhotoOwnershipImmutability`: Validates `uploadedBy` cannot be reassigned.
- `testAdminPhotoPrivileges`: Validates admin can delete and moderate foreign photos.
- `testStoragePathValidation`: Validates strict regex pattern rejecting path traversal.
- `testCrowdReportValidationRules`: Validates level limits $[1, 10]$ and rejects out-of-range ratings.
- `testWeatherReportConditionValidation`: Validates weather enum whitelist.
- `testAdminAuthorizationCheck`: Validates admin authorization truth table.

---

## 14. Known Limitations & Recommendations

1. **Client-Side GPS Spoofing:** Because PandalFinder avoids mandatory paid Cloud Functions, device GPS calculations cannot be cryptographically verified server-side.
2. **Key Rotation Recommendation:** If `secrets.properties` or developer signing certificates are ever updated, rotate keys in Google Cloud Console and Supabase Dashboard.
