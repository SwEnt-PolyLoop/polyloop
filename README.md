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

## Firebase emulator (local testing)

Tests that need Firebase run against the **Firebase Emulator Suite**, a local, in-memory copy of Auth and Firestore. They never touch the real project, and all data is wiped when the emulators stop.

1. Install the Firebase CLI once: `npm install -g firebase-tools` (the Firestore emulator needs Java 21+).
2. Start the emulators from the repo root: `firebase emulators:start`. Ports are in `firebase.json`: Auth 9099, Firestore 8080.
3. Open the Emulator UI at http://localhost:4000 to see the test users and documents.
4. With an Android emulator running, run the instrumented tests: `./gradlew connectedCheck`.

Or start the emulators, run the tests and stop them in one command, as CI does: `firebase emulators:exec --only auth,firestore "./gradlew connectedCheck"`.

Rules for tests:

- A test that uses Firebase must call `FirebaseEmulator.connect()` first (in `app/src/androidTest/.../utils/FirebaseEmulator.kt`). Without it, the test talks to the **real** project. If the emulators are not running, `connect()` fails the test with a message saying how to start them.
- Use a new email for each test (e.g. `test-<random>@epfl.ch`) so leftover accounts never interfere.
- The Firestore emulator currently allows all reads and writes, because there are no Security Rules yet (#34).
- The emulators are reached at `10.0.2.2`, which only works from the Android emulator, not from a physical phone.
