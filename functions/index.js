const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Assigns { role: 'authenticated' } custom claim to newly created Firebase users
 * (including Anonymous users) so Supabase Third-Party Auth assigns the 'authenticated'
 * Postgres role.
 */
exports.setSupabaseRoleClaim = functions.auth.user().onCreate(async (user) => {
  try {
    await admin.auth().setCustomUserClaims(user.uid, {
      role: "authenticated"
    });
    console.log(`Successfully assigned role='authenticated' claim to Firebase UID: ${user.uid}`);
  } catch (error) {
    console.error(`Error setting custom claims for user ${user.uid}:`, error);
  }
});
