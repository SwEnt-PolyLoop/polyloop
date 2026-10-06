<!-- Edited with Claude. -->
# AGENTS.md

Durable rules for any AI agent (and human) working in this repository. Read this before acting.

## The app

**PolyLoop** is a peer-to-peer rental app for EPFL students (`com.swent.polyloop`): students rent out unused belongings and borrow what they need, protected by verified profiles, an in-app wallet (PolyPoints) and a refundable deposit. EPFL SwEnt course project, team of 7, 10 weeks.

- Android, Kotlin, Jetpack Compose, Material 3. minSdk 28.
- Backend is Firebase: Auth (email/password, verified `@epfl.ch` only), Firestore, Cloud Storage (photos, court-ruling PDFs), Cloud Functions (trusted logic; Gemini for dispute proposals and reading court rulings), Cloud Messaging.
- Rental flow: request (rental price reserved) -> lender accepts and enters the deposit agreed in chat (deposit reserved) -> pickup: condition photos + QR scan -> return: condition photos + QR scan -> lender confirms or flags damage -> direct settlement (auto-escalates after 30 days) -> Gemini proposes a split -> court ruling uploaded as PDF, reviewed by an admin. Late items are lost after a 3-day grace period.

Before writing any code, read `docs/ARCHITECTURE.md` and `docs/SCREENS.md`. They are the source of truth for the architecture, the product rules and every screen.

Diagram: `docs/architecture/polyloop_architecture.png` (the SVG next to it is the editable version).

## Rules for agents

1. **Do not make design or technical decisions on your own.** If something you need is not in `docs/`, or the docs are ambiguous, stop and ask. The "Not decided yet" list in ARCHITECTURE.md names known gaps.
2. **Respect the layers.** Each screen (Compose) only talks to its own ViewModel. ViewModels call the repositories they need directly; they never call each other, and there is no domain/use-case layer.
3. **One ViewModel per screen, one repository per feature.** There are 13 screens, each with its own ViewModel, and 8 repositories, one per type of data (Auth, Profile, Listing, Rental, Chat, Handover, Dispute, Wallet). `docs/ARCHITECTURE.md` lists the repositories each ViewModel uses; keep that list up to date. Do not create new ViewModels or repositories without asking.
4. **If the architecture changes**, update `docs/ARCHITECTURE.md`, `docs/SCREENS.md`, the diagram and, if a rule changes, this file in the same pull request as the code.

## Rules that must never be broken

- **Rental status, reserved amounts, deposits, wallet balances and dispute outcomes change only in Cloud Functions.** The client never writes them, and the Firestore Security Rules block it.
- The EPFL domain and email verification are enforced in the Security Rules too, not only in the app.
- A rental's chat and exact pickup location are visible only to its two participants, and the location only once the rental is accepted. Browsing shows approximate areas only.
- A listing can be edited or deleted only by its owner, and not while a rental of it is accepted or active.
- All photos (listing and condition photos) come only from the in-app camera, never from the gallery.
- Offline mode: current rentals, lent items, own listings, the wallet and recent chats stay readable; photos and QR scans are stored in the local upload queue and synced later; show the offline banner; disable browsing, recharging, payments and messaging while offline.

## Architecture rules

- Keep the **MVVM** separation. **ViewModels never import Firebase** or a repository implementation; they depend on repository interfaces. Firebase lives only in the `model/` repositories (and the local upload queue).
- `model/` holds data and repositories, `ui/` holds screens and their ViewModels, `ui/theme/` holds the theme.
- User-facing text goes in `strings.xml`, not hardcoded in composables.
- Do not edit generated code.

## Definition of done

- The feature matches the acceptance criteria of its GitHub issue.
- **All new code comes with tests.**
- Run before pushing: `./gradlew ktfmtFormat`, then `./gradlew ktfmtCheck` and `./gradlew check`.
- UI and instrumented tests must pass `./gradlew connectedCheck jacocoTestReport` with an Android emulator running (CI uses API 34).
- CI must be green, including the **SonarCloud Quality Gate**: at least 80% coverage on new code and Security rating A. Sonar treats every changed line as new code, even formatting or import changes, so touch only what you need.

## Tests

- Unit tests go in `app/src/test`, instrumented and Compose tests in `app/src/androidTest`. Mirror the source package and name the file `<Thing>Test.kt`.
- Compose tests use `v2.createComposeRule()` with `@get:Rule val composeTestRule`, find nodes by test tag (constants in `resources/C.kt`, object `C.Tag`) and use descriptive camelCase names like `displayHasCorrectDefaultValue`.
- Changes to Firestore or Storage Security Rules come with tests.

## How to work

- One **bounded, reviewable** change per PR, tied to one GitHub issue. Read the issue first (`gh issue view <N>`) and put `Closes #<N>` in the PR description. If the change sprawls across unrelated files, split it.
- Branch names: `<type>/<issue>-<short-description>`, e.g. `chore/26-cleanup-template-leftovers`.
- Commits follow Conventional Commits: `feat:`, `fix:`, `test:`, `chore:`, `build:`, `style:`, `docs:`, then an imperative subject of at most 72 characters. Add a body when the subject is not enough.
- **No AI signature**: no `Co-authored-by` or "generated with" lines in commits or PR descriptions.
- Stage only the files you changed; never `git add .` or `git add -A` (it can pull in local config like `local.properties`).
- Read failing tests and CI logs carefully and iterate until everything passes.

## Never do, ask first

- **Never** commit `google-services.json`, tokens or `local.properties`. CI reads the secrets `GOOGLE_SERVICES` and `SONAR_TOKEN`.
- **Never** lower coverage thresholds, add Sonar exclusions, disable lint or tests, use `--no-verify`, or force-push to `main` just to get green.
- **Ask first** before changing dependencies or versions, `.github/workflows/ci.yml`, Security Rules, or the Firestore data model.

## Your role (for the human)

You provide the goal, the context, the acceptance criteria and the permissions. The agent plans, acts and observes. **You review the diff, and you own every line you submit.** "The agent wrote it" is not a defence.
