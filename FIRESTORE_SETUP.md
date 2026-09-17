# Firestore crowd reports

The app intentionally has no Firebase credentials in source control. Link this Android
application to your Firebase project and place the generated `google-services.json` in
`app/` before enabling production crowd reporting.

The client writes only `pandalId`, `crowdLevel`, and a server timestamp. It ignores reports
older than 90 minutes and limits one device to one report per pandal every five minutes.

Use Firestore rules appropriate for your rollout. At minimum validate that `crowdLevel` is
an integer from 1 to 10, `pandalId` is a known identifier, and that a report cannot supply an
arbitrary historical timestamp. Public unauthenticated writes should be protected by Firebase
App Check before launch.
