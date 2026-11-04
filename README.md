<!-- Edited with Claude. -->

# PolyLoop

Students often spend money they don't have on things they'll barely use: a tent for one trip, a suit for one gala, a calculator for one exam. Meanwhile, those same items sit idle in dorm rooms nearby.

PolyLoop is a peer-to-peer rental app where students rent out their unused belongings and borrow what they need for exactly as long as they need it, from sports gear and bikes to cameras, instruments, and formal wear.

Our insight is that students need access, not ownership, and that trust is the real barrier to sharing. PolyLoop solves this with verified student profiles, in-app payments, and a deposit that's automatically released upon return.

We target budget-conscious university students:

- Léa, 20, an exchange student who needs a bike for one semester but won't buy one she'll leave behind
- Marco, 23, who wants to earn rent money from the camera and snowboard he rarely uses
- Sofia, 18, a first-year who needs a graphing calculator for exams without blowing her budget

PolyLoop turns every campus into a shared closet.

## Designs 
[Figma mockups](https://www.figma.com/design/zIY3U7Iz20RxrVlxwa0fzB/SwEnt-PolyLoop?node-id=0-1&t=kkSRvAukBZsD3PTr-1)

## Google Maps key

The maps use the Maps SDK for Android, which needs an API key. The key stays out of git: put it in `local.properties` (already ignored), as `MAPS_API_KEY=<your key>`. Without it the app still builds and the tests pass, but the maps show no tiles. This is how CI builds the app.

To get a key, once, in the project's Google Cloud console (the same project as Firebase): enable the **Maps SDK for Android**, then create an API key restricted to Android apps, with the package `com.swent.polyloop` and the SHA-1 of your debug certificate (shown by `./gradlew signingReport`). Each teammate adds their own SHA-1 to the key.

The **Build APK** workflow (`.github/workflows/apk.yml`) reads two repository secrets, so its APK shows the maps: `MAPS_API_KEY`, the key, and `DEBUG_KEYSTORE`, a debug keystore encoded in base64, so that every run signs with the same certificate. Its SHA-1 must be on the key too. Without these secrets the workflow still builds an APK, with a warning, and its maps show no tiles.

## Firebase emulator (local testing)

Tests that need Firebase run against the **Firebase Emulator Suite**, a local, in-memory copy of Auth, Firestore and Cloud Storage. They never touch the real project, and all data is wiped when the emulators stop.

1. Install the Firebase CLI once: `npm install -g firebase-tools` (the Firestore emulator needs Java 21+).
2. Start the emulators from the repo root: `firebase emulators:start --only auth,firestore,storage`. Ports are in `firebase.json`: Auth 9099, Firestore 8080, Storage 9199. Without `--only`, the Functions emulator starts too, and once `functions/` is built it runs `geocodeListingAddresses` on the test listings; the app's tests don't need it.
3. Open the Emulator UI at http://localhost:4000 to see the test users, documents and uploaded files.
4. With an Android emulator running, run the instrumented tests: `./gradlew connectedCheck`.

Or start the emulators, run the tests and stop them in one command, as CI does: `firebase emulators:exec --only auth,firestore,storage "./gradlew connectedCheck"`.

Rules for tests:

- A test that uses Firebase must call `FirebaseEmulator.connect()` first (in `app/src/androidTest/.../utils/FirebaseEmulator.kt`). Without it, the test talks to the **real** project. If the emulators are not running, `connect()` fails the test with a message saying how to start them.
- Use a new email for each test (e.g. `test-<random>@epfl.ch`) so leftover accounts never interfere.
- The emulators enforce `firestore.rules` and `storage.rules`, as listed in `firebase.json` (see "Security rules" in `docs/ARCHITECTURE.md`). Every read, write and upload needs a verified @epfl.ch account, so a test signs one in first with `FirebaseEmulator.signUpVerified()`. Test data that belongs to another user, which the rules refuse, is written with `FirebaseEmulator.createDocumentAsAdmin`, which skips them.
- The emulators are reached at `10.0.2.2`, which only works from the Android emulator, not from a physical phone.

## Cloud Functions

The server code is in `functions/` (see "Technical choices" in `docs/ARCHITECTURE.md`). From `functions/`, `npm ci` installs it and `firebase emulators:exec --only firestore "npm test"` runs its tests, as CI does. Tests that read and write Firestore refuse to run without the emulator, so they never touch the real project, and the Geocoding API is faked, so no key is needed.

Before the first deploy of `geocodeListingAddresses`, once, in the project's Google Cloud console: enable the **Geocoding API**, create an API key restricted to it, and set a budget alert on the billing account. Then store the key with `firebase functions:secrets:set GEOCODING_API_KEY` and deploy with `firebase deploy --only functions`.
