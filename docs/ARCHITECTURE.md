<!-- Edited with Claude. -->
# PolyLoop architecture

## Layers

```
UI layer        Compose screens (15 screens, see SCREENS.md)
                  │  observe state / send user actions
ViewModel layer 15 screen ViewModels (one per screen) + DrawerViewModel (navigation drawer)
                  │  call the repositories they need (no domain layer)
Data layer      10 repositories + local upload queue (WorkManager)
                  │
Device          GPS · network status
Firebase        Auth · Firestore · Cloud Storage · Cloud Functions · FCM
External        Google Maps Platform (Maps SDK in app, Geocoding API from Functions) · Gemini API (from Functions)
```

## Repositories: one per feature

One repository per type of data, shared by every ViewModel that needs it. ViewModels and composables never read a data source (Firebase, GPS, network status) directly; they always go through a repository.

| Feature | Repository | Talks to |
| --- | --- | --- |
| Auth | AuthRepository | Firebase Auth |
| Profile | ProfileRepository | Firestore, Cloud Storage |
| Chat | ChatRepository | Firestore; Firebase Auth (the sender of a message) |
| Listing | ListingRepository | Firestore, Cloud Storage |
| Rental | RentalRepository | Firestore, Cloud Functions, Local upload queue → Cloud Storage (mid-rental photos) |
| Handover | HandoverRepository | Firestore, Cloud Storage (directly until the local upload queue exists), Cloud Functions; Firebase Auth (who takes a photo) |
| Dispute | DisputeRepository | Firestore, Cloud Storage, Cloud Functions |
| Wallet | WalletRepository | Firestore, Cloud Functions |
| Location | LocationRepository | The phone's GPS |
| Connectivity | ConnectivityRepository | The phone's network status |

`model/RepositoryProvider` creates the repositories, so the whole app shares one instance of each. Each repository is created on first use, so Firebase is not touched until it is needed. Tests can replace a repository with a fake by assigning it before setting content, then call `reset()`. It holds the repositories implemented so far (Auth, Listing, Profile, Chat, Rental, Handover, Connectivity and Wallet); add each new repository to it when it is implemented. It also exposes the shared `FirebaseStorage` instance, for the repositories that upload photos and PDFs. Repositories that read the phone (Connectivity, later Location) need the app context: `PolyLoopApplication` passes it to `RepositoryProvider.init` at startup.

## ViewModels: one per screen

Each screen has its own ViewModel, which holds that screen's state and calls the repositories it needs through their interfaces. ViewModels never call each other, and there is no use-case layer for now. Every ViewModel may read the signed-in user from AuthRepository and the online/offline status from ConnectivityRepository (for the offline banner and the actions disabled offline); that is not repeated below.

| Screen | ViewModel | Repositories it uses |
| --- | --- | --- |
| Sign-up / login | AuthViewModel | Auth; Profile (creates the profile on the first verified sign-in) |
| Profile & reviews | ProfileViewModel | Profile; Listing ("My listings", and someone else's published listings); Rental (where each of the own listings stands); Auth ("Sign out") |
| Edit profile | EditProfileViewModel | Profile (loads the profile, uploads the new photo, saves the name and the photo's URL) |
| Wallet | WalletViewModel | Wallet |
| Chat list | ChatListViewModel | Chat (the item and the other person are copied on each rental, see "Firestore schema") |
| Chat | ChatViewModel | Chat; Rental (link to Rental detail, sending disabled during a dispute, the offer card and its answers); Profile (the other person); Listing (the item) |
| Browse | BrowseViewModel | Listing; Location (distance and map position) |
| Listing detail | ListingDetailViewModel | Listing (the item and its booked days); Rental (sending the request); Profile (the owner's name, photo and rating); Auth (the borrower sending the request) |
| Create / edit listing | CreateListingViewModel | Listing; Rental (editing locked while a rental is accepted or active); Auth (the owner publishing the listing) |
| My rentals | MyRentalsViewModel | Rental (the item, the other person and the areas are copied on each rental, see "Firestore schema") |
| Review request | ReviewRequestViewModel | Rental (the request, sending the offer, declining, the lender's other requests whose dates overlap); Listing (the item value, shown as the suggested deposit); Profile (the borrower's rating) |
| Rental detail | RentalDetailViewModel | Rental (status, actions, deposit, meeting point, mid-rental photos); Listing (the item); Profile (the other person, the review prompt) |
| Handover | HandoverViewModel | Handover; Rental (phase, role, deposit held); Dispute ("Flag damage") |
| Dispute | DisputeViewModel | Dispute; Handover (pickup and return photos, not used yet); Rental (the item, the other person and the user's role, the deposit) |
| Admin court ruling | AdminCourtRulingViewModel | Dispute; Handover (the photos); Rental (the deposit); Listing (the item); Profile (both parties) |
| Navigation drawer (every screen) | DrawerViewModel | Profile (the signed-in user's name, photo and rating in the header) |

If a ViewModel needs a repository that is not listed here, add it to this table in the same pull request.

DrawerViewModel is the one ViewModel not tied to a screen: the navigation drawer opens from the top bar of every screen, so `PolyLoopApp` creates it once for the whole app and refreshes it on each navigation, which follows sign-ins and sign-outs. Whether the drawer lists Court rulings comes from the navigation (`isAdmin`), not from this ViewModel, until the admin claim is read.

## Core rule: the app expresses intent, Cloud Functions decide

The app writes only content it owns (listings, profile fields, chat messages, photos). Anything that moves money or changes who holds an item is done by Cloud Functions, called from repositories:

- rental status changes (request, accept, decline, cancel, pickup, return, close)
- deposit offers and counteroffers
- reserving and releasing the rental price and the deposit
- wallet balance changes (recharge, payments, late fees, refunds)
- dispute outcomes and applying court rulings

Firestore security rules must reject client writes to those fields. One temporary exception: until the `requestRental` Cloud Function exists, the app creates a rental request itself (see "Firestore schema" below).

## Rental statuses

Requested → Accepted → Active → Returned → Closed, plus Declined, Cancelled, Overdue, Lost, Disputed, Frozen.

## Product rules

**Accounts**
- Only @epfl.ch emails, checked with a regex, plus Firebase email verification. Name asked at sign-up; photo added later in Profile.
- Sign-up passwords need at least 8 characters (at most 4096, refused as too long), an uppercase letter, a lowercase letter, a number from 0 to 9 and a special character from a fixed list (see SCREENS.md); the app checks them before calling Firebase, whose password policy (enforced on the project) accepts every password that follows them. Logging in does not check them, so existing accounts keep working.
- An account must verify its email within 5 minutes of signing up, or it is deleted, so nobody can block an address by signing up with it (someone else's, or a typo). The Cloud Function `deleteExpiredSignUpsEvery5Minutes` checks every 5 minutes, so an expired account is gone 5 to 10 minutes after sign-up. Unverified accounts cannot write to Firestore or Storage, so only the Auth account is deleted.
- Admins are identified by a Firebase custom claim.

**Money (PolyPoints)**
- In-app currency: PolyPoints (PP), 1 CHF = 1 PP. Recharge on the Wallet screen with a whole CHF amount from 1 to 99,999 (the same cap as a listing price); a recharge that would overflow the balance is refused. Payments are simulated. No withdrawals.
- Sending a request reserves the rental price (price per day × days) from the borrower's balance; the request fails if the balance is too low. The wallet shows that price as `heldForRentals` and the deposits as `heldAsDeposits`; `reserved` is their sum, computed and never stored, and `available` + `reserved` is the user's total. A missing field reads as 0. (Not yet: until then, `payLender` takes the price from `available`.)
- The lender is paid the rental price once the item has been picked up (`payLender`); until the price is reserved when a request is sent, it is taken from `available`, afterwards it will come from `heldForRentals`.
- The deposit is negotiated before acceptance, and only the deposit: the rental price is fixed. The item value is shown as the suggested deposit. An offer or a counteroffer is a whole amount from 1 to 99,999 PP (the same cap as a listing price).
    1. On a request without an offer, the lender either sends an offer (the deposit and both exact addresses) or declines.
    2. On the lender's offer, the borrower accepts, declines or sends one counteroffer.
    3. On a counteroffer, the lender accepts or declines. There is no further round.
- The rental stays `REQUESTED` during the negotiation. Accepting makes it `ACCEPTED`; the deposit is reserved then, and acceptance fails if the balance is too low. Declining an offer or a counteroffer makes it `DECLINED`.
- An offer or a counteroffer expires after 1 week without an answer, or on the rental's first day if that comes sooner, and the rental becomes `DECLINED`. It cannot be accepted after that. A request without an offer does not expire.
- The wallet is stored in Firestore (see "Firestore schema"). Accepting an offer (`respondToOffer`, `ACCEPT`) holds the deposit in the borrower's wallet, in the same transaction, and fails with an insufficient funds error (`failed-precondition`) if the borrower's `available` balance is too low, leaving the rental `REQUESTED`. The rental price is not reserved yet (`heldForRentals` stays 0). The `payLender` Cloud Function pays the lender, but nothing calls it yet. Nothing releases or splits a held deposit yet.

**Listings and booking**
- Price per day. The lender sets an availability window; the borrower picks dates inside it with a date picker.
- 3 to 10 listing photos, taken with the in-app camera only (no gallery).
- Pickup and drop-off addresses are typed as text; a Cloud Function geocodes them when the listing is saved. Only the area is public (its name and a point rounded to about 1 km); the exact address is shown after acceptance.
- A 1-day buffer comes before and after every booking. Booked days and buffer days are not selectable.
- Accepting a request auto-declines other requests whose dates overlap (buffer included).
- Your own listings never appear in Browse. Categories are a hardcoded list: Electronics, Sports & outdoor, Books & course material, Tools & DIY, Kitchen & home, Music, Other.

**Cancelling**
- Either party can cancel until the day before pickup. On the pickup day the rental is locked.
- If the item is still not back from the previous rental on the pickup day, the rental is auto-cancelled and the borrower gets the reserved price and deposit back.

**Handover**
- QR scan at pickup and at return. The person receiving the item scans: at pickup the lender shows the code and the borrower scans; at return the borrower shows and the lender scans.
- At each handover, each person takes their own 3 to 20 condition photos; the scan is disabled until they have 3. Photos can also be taken mid-rental from Rental detail.
- Offline: photos and QR scans go into the local upload queue and sync later. The server trusts the app's order (a scan is accepted even if its photos are still uploading).
- After the return scan, the lender chooses "Item is fine" (full deposit released) or "Flag damage" (opens a dispute).

**Late returns**
- 3-day grace period after the deadline, with reminders. Each late day costs the price per day + 2 PP, taken from the deposit and paid to the lender. Fees stop when the deposit is used up. After 3 days the item is Lost and the rest of the deposit goes to the lender.

**Disputes**
1. Direct settlement: parties propose splits in percent; accept or counter. Auto-escalates after 30 days without agreement; either party can escalate sooner. It is the only stage that escalates, by hand or on its own.
2. LLM proposal: a Cloud Function sends pickup and return photos to Gemini and gets a split + reasoning. Both parties accept or reject.
3. Court: once the dispute reaches court, because either party rejected the LLM proposal, the deposit is frozen. Either party uploads the court ruling PDF; Gemini pre-fills the split; an admin reviews it on the Admin court ruling screen and applies or rejects it.
- A split is applied in whole PolyPoints: the borrower's share is rounded down and the lender gets the rest, so the shares always add up to the deposit (50% of 33 PP gives 16 PP to the borrower, 17 PP to the lender). The Cloud Functions that apply a split must round the same way as the app.
- Chat is disabled while a dispute is open. Reviews (1–5 stars, no text for now) are prompted when a rental closes, however it closed.
- For now an agreement only settles the dispute and closes the rental, and no money is released or split: the deposit is held when an offer is accepted, but nothing releases or splits a held deposit yet, so it stays held after an agreement.
- For now nothing produces the LLM proposal: an escalated dispute stays in `LLM_PROPOSAL`, with no way forward, until the Gemini Cloud Function exists. Nothing escalates a dispute by itself after 30 days yet either: only a participant's "Escalate" does.

## Technical choices

Packages: `model/` (data and repositories), `ui/` (screens and their ViewModels), `ui/theme/` (theme). ViewModels depend on repository interfaces and never import Firebase. New Firestore repositories use the shared helpers in `model/firestore/FirestoreHelpers.kt` (reading fields, errors, write timeout, listening to a query) instead of copying them; Rental, Listing, Profile, Chat and Handover use them.

Cloud Functions live in `functions/`, a separate TypeScript project (Node 22, `firebase-functions` v2, Jest). `src/index.ts` exports every function and sets the region, `europe-west1`, next to the Firestore database (`eur3`). In `functions/`: `npm ci` installs, `npm run lint` type-checks, `firebase emulators:exec --only firestore "npm test"` runs the tests on the Firestore emulator (external APIs are faked), and `firebase deploy --only functions` deploys (needs the Blaze plan). API keys live in Secret Manager. CI runs the install, lint, build and test steps in its own job. The app asks `serverToday` for the date to decide when Start Pickup opens on Rental detail (from the next PR).

**`reserveDeposit({rentalId})`** holds the deposit of an accepted rental. Accepting an offer already holds it (see below), so this is a safe repeat: it finds the row and succeeds without a second charge. The caller must be signed in with a verified `@epfl.ch` account and be the rental's borrower or its lender, and the rental must be `ACCEPTED`, unless the deposit is already held. The checks run in this order: the rental exists, the caller is a participant, the borrower is valid, the row already exists (the call succeeds, even if the rental has moved on, e.g. to `ACTIVE`), the status is `ACCEPTED`, the deposit is valid, the balance covers it. The amount (`deposit`), the people (`borrowerId`, `ownerId`) and the item title (`listingTitle`) come from the rental, never from the request. In one Firestore transaction on the borrower's wallet, `available -= deposit`, `heldAsDeposits += deposit`, and one `DEPOSIT_HELD` row is added to the borrower's history with the id `deposit_held_<rentalId>`, the amount `-deposit`, the item title and a server timestamp. If `available` is below the deposit, the call fails with an insufficient funds error (`failed-precondition`) and writes nothing. It is idempotent: the row is looked for in the same transaction, so a second call for the same rental changes nothing and succeeds. It writes only the borrower's wallet, even when the lender calls it, and returns nothing, so the lender learns only whether the borrower's balance covers the deposit (the insufficient funds error), never the balance itself. The read and write steps on a wallet are in `functions/src/wallet.ts`, for the other functions that move PolyPoints: `readWallet`, `requireFunds` (the funds check, called after the other checks) and `writeDepositHold`. `respondToOffer` with `ACCEPT` uses the same steps inside its own transaction: the borrower's wallet is read with the other reads, and the check, run before any write, throws the same insufficient funds error with nothing written (the rental stays `REQUESTED`, the offer stays, `bookedRanges` is untouched and no overlapping request is declined). The borrower pays whoever accepts, and the amount is the offer's: after a counteroffer, the counter amount. It holds only the deposit, `DEPOSIT_HELD` row `deposit_held_<rentalId>` included; it does not reserve the rental price, call `payLender`, or release or split the deposit.

**`payLender({rentalId})`** pays the lender the rental price once the item has been picked up. The caller must be signed in with a verified `@epfl.ch` account and be the rental's borrower or its lender, and the rental must have been picked up (status `ACTIVE` or any later one: `RETURNED`, `OVERDUE`, `DISPUTED`, `FROZEN`, `LOST`, `CLOSED`), unless the payment was already made; a payment missed while the rental was `ACTIVE` (app killed, offline scans synced together) can still be made up afterwards. The checks run in this order: the rental exists, the caller is a participant, the borrower and the lender are valid and different, the payment already exists (the call succeeds, whatever the status is by now), the status is one of those above, the price is valid, the borrower's balance covers it. The amount (`totalPrice`), the people (`borrowerId`, `ownerId`) and the item title (`listingTitle`) come from the rental, never from the request. In one Firestore transaction, the borrower's `available -= totalPrice` and the lender's `available += totalPrice` (their wallet is created if missing), and one `RENTAL_PAYMENT` row with the id `rental_payment_<rentalId>` is added to each history, `-totalPrice` for the borrower and `+totalPrice` for the lender, with the item title and a server timestamp. Until the rental price is reserved when a request is sent, it is taken from `available`, not from `heldForRentals`. If `available` is below the price, the call fails with an insufficient funds error (`failed-precondition`) and writes nothing. It is idempotent: the borrower's row is looked for in the same transaction, so a second call changes nothing and succeeds. It returns nothing, so the lender learns only whether the borrower's balance covers the price, never the balance itself.

| Area | Choice |
| --- | --- |
| Architecture | MVVM without a domain layer: 15 screen ViewModels, each calling the repositories it needs |
| UI state | Each ViewModel exposes its screen state as a `StateFlow`, collected with `collectAsStateWithLifecycle()`; user actions are method calls (unidirectional data flow) |
| Navigation | Single activity, Navigation 2 (`navigation-compose`). Navigation 3 is Google's newer option, not adopted for now |
| Dependency injection | Manual constructor injection: repositories and ViewModels receive their dependencies in the constructor (ViewModels through a `ViewModelProvider.Factory`), so tests can pass fakes. `RepositoryProvider` creates the shared repository instances |
| Server logic | Cloud Functions (TypeScript, in `functions/`, region `europe-west1`) are the only writers of rental status, deposits and balances |
| Offline cache | Firestore's built-in offline persistence |
| Upload queue | WorkManager, for offline photos and QR scans |
| Camera and QR | CameraX for photos; ML Kit code scanner to scan, ZXing to generate QR codes |
| Maps | Google Maps SDK for Android in the app, through Maps Compose (`maps-compose`). Its key comes from `MAPS_API_KEY` in `local.properties`, or in the environment for the Build APK workflow (see the README); without it, as on CI, the maps show no tiles |
| Geocoding | Cloud Function `geocodeListingAddresses`, when a listing's exact addresses are written (Geocoding API, key `GEOCODING_API_KEY`) |
| "Near me" | Geohash queries with the GeoFire utilities |
| LLM | Gemini API, called only from Cloud Functions (dispute proposals, court-ruling reading) |
| Scheduled jobs | Every 5 minutes: deleting accounts not verified in time (`deleteExpiredSignUpsEvery5Minutes`). Hourly: expiring deposit offers (`expireOffers`). Hourly Cloud Function: 30-day escalation, deadline reminders, late fees, lost items, auto-cancelling rentals blocked by a late return |
| Notifications | FCM |
| Admins | Firebase custom claim |

## Firestore schema

Dates without a time are stored as `YYYY-MM-DD` strings, prices as whole PolyPoints. Date-times (`createdAt`, `lastMessageAt`, `sentAt`, `takenAt`) are Firestore Timestamps, set with `FieldValue.serverTimestamp()`; reads use `ServerTimestampBehavior.ESTIMATE`, because until the server confirms a write a listener otherwise sees these fields as null. When a new field is added to an existing collection, reading must give it a default value: documents written before it do not have it.

| Path | Fields | Notes |
| --- | --- | --- |
| `users/{uid}` | `name`, `email`, `photoUrl`, `averageRating`, `reviewCount` | Missing fields read as `""` or 0. `photoUrl` is the download URL of the profile photo in Storage, or `""` without one. |
| `listings/{listingId}` | `ownerId`, `title`, `description`, `category`, `photoUrls`, `pricePerDay`, `itemValue`, `availableFrom`, `availableTo`, `status`, `pickupArea`, `dropOffArea`, `pickupPoint`, `dropOffPoint`, `bookedRanges` | Public. `bookedRanges` is a list of `{start, end}` (`YYYY-MM-DD`, both days booked, the buffer days around it not included); missing reads as an empty list. It holds accepted rentals only: a pending request blocks no day. Each range copies the `startDate` and `endDate` of an accepted rental, which stay in `rentals/{rentalId}` to tell who has the item and when; the copy on the listing is public so that anyone can see which days are free without reading other people's rentals. Only Cloud Functions write it; the app never does, so it stays empty until the Function that accepts rentals exists. The areas and points: see below. |
| `listings/{listingId}/private/addresses` | `pickup`, `dropOff` | Exact addresses, readable only by the owner. |
| `rentals/{rentalId}` | `listingId`, `listingTitle`, `listingPhotoUrl`, `ownerId`, `ownerName`, `borrowerId`, `borrowerName`, `participants`, `startDate`, `endDate`, `status`, `pricePerDay`, `totalPrice`, `pickupArea`, `dropOffArea`, `pickupAddress`, `returnAddress`, `deposit`, `lateFees`, `offer`, `createdAt`, `lastMessage`, `lastMessageAt` | See below. |
| `rentals/{rentalId}/private/addresses` | `pickup`, `dropOff` | The exact addresses of an offer, no client access. |
| `rentals/{rentalId}/messages/{messageId}` | `senderId`, `text`, `sentAt`, `type` (`USER` or `SYSTEM`) | One conversation per rental. |
| `rentals/{rentalId}/photos/{photoId}` | `takenBy`, `phase` (`PICKUP` or `RETURN`), `takenAt`, `url` | Condition photos, readable by both participants; the app shows each person only their own. Each adds only their own, pickup photos only while the rental is Accepted; nobody edits or deletes one. |
| `wallets/{uid}` | `available`, `heldAsDeposits`, `heldForRentals` | Whole PolyPoints; a missing field, or no document, reads as 0, and a negative value reads as 0. Written by Cloud Functions only. |
| `wallets/{uid}/transactions/{id}` | `type` (a `WalletTransactionType` name), `amount`, `itemTitle`, `date` | One row of the user's history. `amount` is a signed whole number from that user's view, never 0; `itemTitle` is `""` for a recharge; `date` is a server Timestamp. The app skips a row that breaks this instead of failing the list. Written by Cloud Functions only. |
| `disputes/{rentalId}` | `stage`, `proposals`, `escalatesAt`, `escalatedBy`, `agreedAt` | See below. Readable by the rental's two participants only, written by Cloud Functions only. |

Listings:
- `pickupArea` and `dropOffArea` are area names (e.g. "Ecublens"); `pickupPoint` and `dropOffPoint` are GeoPoints. The app creates a listing with empty names and no points; the `geocodeListingAddresses` Cloud Function fills them in. Each time `listings/{listingId}/private/addresses` is written, it geocodes each changed address (in Switzerland only) and stores its area name and its point rounded to a 0.01° grid (about 1.1 × 0.8 km around EPFL). The exact address and point never reach the public listing. A lookup that fails (e.g. the Geocoding API is down or takes more than 10 seconds) is retried for up to a day, and a result is written only if its address has not changed since, so the newest edit wins even if its run finishes first.
- Until an address is geocoded, or when it is blank, not found or its geocoding fails, its name and point are missing, and screens show a placeholder.

Rentals:
- The listing title, both names and the public areas are copied when the rental is created, so My rentals and the chat list need no other read. A missing owner name is stored as `""`; screens show a translated placeholder.
- `participants` is `[ownerId, borrowerId]`; the chat list queries it with `array-contains` only and sorts by `lastMessageAt` in the app, so it needs no composite index.
- My rentals queries `borrowerId` or `ownerId` only and sorts newest first in the app (a user has few rentals), so it needs no index.
- `lastMessageAt` is set to the same server timestamp as `createdAt` when the rental is created: the chat list sorts by it, and leaves out a rental that lacks it.
- `pickupAddress` and `returnAddress` stay empty until the rental is accepted; the borrower cannot read the exact addresses before that. The lender's offer stores them in `private/addresses`, and acceptance copies them onto the rental.
- `deposit` is the accepted amount in PolyPoints; missing reads as 0.
- `lateFees` is the total of the late fees taken from the deposit, in PolyPoints. Only Cloud Functions will write it, the scheduled job that charges late fees, once a return is late, so a rental that was never late has no such field, and missing reads as 0.
- `offer` is the offer waiting for an answer: `amount`, `authorId`, `isCounter`, `createdAt`, `expiresAt`, and on a counteroffer `previousAmount`, the lender's offer. It is missing when no offer is waiting, and removed when the negotiation ends. `expireOffers` queries `offer.expiresAt` alone, so it needs no composite index, and answering also refuses an expired offer, in case `expireOffers` has not run yet.
- Each step of the negotiation adds a `SYSTEM` message to the chat, written by the Cloud Functions; the chat's offer card reads `offer`, so there is no new message type.
- **Temporary exception to the core rule:** until the `requestRental` Cloud Function exists, the app creates the rental itself, with status `REQUESTED` only and no money reserved. The security rules allow only that creation, and afterwards only `lastMessage` and `lastMessageAt` can be changed by the participants. Until then, the dates are checked against `bookedRanges` only in the app (Listing detail reads them again from the server just before sending), so a client that skips this check is not stopped.

Wallet:
- `WalletRepositoryFirestore` only reads, with plain snapshot listeners, so the wallet and the history stay readable offline from the cache. The history is not sorted there (`WalletViewModel` sorts it). An invalid uid reads as an empty wallet and history. `recharge` always fails ("not available yet") until a recharge Cloud Function exists.
- Until recharging exists, give a test user PolyPoints by hand: edit `wallets/{uid}` in the Firebase console, or write it with a script against the emulator (Admin SDK or the emulator's REST API with the `Bearer owner` token, which skips the rules). Add a matching `RECHARGE` row in `transactions` if the history should add up to `available`.

Disputes:
- One per rental, at the rental's id. Nothing is copied from the rental: the item, both participants and the deposit are read from it.
- `stage` is `SETTLE`, `LLM_PROPOSAL` or `COURT`. `proposals` lists every split, oldest first, each `{authorId, lenderPercent, createdAt}` with `lenderPercent` a whole number from 0 to 100; `createdAt` is set by the Cloud Function from its own clock, because a server timestamp is not allowed inside a list. `escalatesAt` is when direct settlement escalates by itself: 30 days after the dispute opens, to be set by the Cloud Function that will open it (not written yet), and not changed afterwards. `escalatedBy` is the uid of the participant who escalated, or null when it escalated by itself or has not escalated. `agreedAt` is set when a split is accepted, and null before; it, not `stage`, tells whether the dispute is settled (an agreement leaves `stage` at `SETTLE`).
- `proposeSplit({rentalId, lenderPercent})` adds a split, refused if it is the latest one or if the dispute already holds 500 (far more than a real negotiation; it keeps a script or a bug from growing the document towards Firestore's 1 MiB limit). `acceptSplit({rentalId, lenderPercent})` accepts the latest split, which must be the other participant's and give the lender `lenderPercent`; it sets `agreedAt` and closes the rental. `escalateDispute({rentalId})` moves the dispute to `LLM_PROPOSAL`. All three refuse a settled dispute, one no longer in `SETTLE`, and one whose rental is not `DISPUTED` (only the Function that opens a dispute sets that status, so an agreement never overwrites another one). A request that lost a race fails with `failed-precondition` and `details.reason`: `PROPOSAL_CHANGED` (the latest split is not the one accepted) or `STAGE_CHANGED` (the dispute was settled or left `SETTLE`); the app reads the reason, never the message.
- An agreement and an escalation add a `SYSTEM` message to the rental's chat; a proposal adds none.

Listing photos uploaded by the app go to Cloud Storage under `listings/{listingId}/`, as `0.jpg` to `9.jpg`, each tagged with the uid of the user who uploaded it (custom metadata `uploader`), so the Storage rules can check the owner and the uploader. Photos added before this rule live in `listing_images/` and keep working through their download URLs. Condition photos go to `rentals/{rentalId}/photos/{photoId}.jpg`, named after their Firestore document. A profile photo goes to `users/{uid}/profile.jpg`, one file per user that each new photo replaces; replacing it gives a new download URL, so the new photo is not hidden by an image cache.

## Security rules

`firestore.rules` and `storage.rules` are listed in `firebase.json`, so the emulators enforce them; `FirestoreRulesTest` and `StorageRulesTest` (in `androidTest/.../rules/`) check them. Every read and write needs a verified `@epfl.ch` account. Beyond the notes in the schema above:
- A listing must pass the same checks as `Listing.canPublish` (3 to 10 photo URLs, all strings, prices from 1 to 99,999 PP, `availableFrom` before `availableTo`, a known category and status). Clients never write `bookedRanges` (a Cloud Function will, on acceptance) and never change `ownerId`. They create `pickupArea` and `dropOffArea` empty, never write `pickupPoint` or `dropOffPoint`, and never change any of the four afterwards (`geocodeListingAddresses` does). A listing cannot be created at an id whose `private/addresses` is left over from a deleted listing.
- A rental request must match its listing, which must be `PUBLISHED`: same owner, title, first photo (`""` if the listing has none), price per day and areas, dates inside the availability window, and `totalPrice` equal to the price per day × the number of days.
- Clients never write a rental's `deposit`, `offer`, `pickupAddress` or `returnAddress`, and have no access to `rentals/{rentalId}/private/addresses`.
- Wallets: a user reads only their own `wallets/{uid}` and its `transactions`; nobody writes them from the app, the owner included (`allow write: if false`). Cloud Functions write them.
- A dispute is read only by the two participants of its rental (`participants`), and never written by a client.
- Profiles: a user creates and edits only their own, with their own email; `averageRating` and `reviewCount` are left to Cloud Functions.
- Storage: listing photos must be images under 10 MB (the in-app camera takes about 1440 × 1080 JPEGs at quality 80), named `0.jpg` to `9.jpg` and tagged with the uid of the user writing them. They are uploaded before the listing document exists, so a folder whose listing does not exist yet is open to any verified user; once it exists, only the owner may change it. A photo can be replaced or deleted only by the user who uploaded it, even once its listing is deleted; photos uploaded before the tag existed have none, so only an admin can change or delete them. These rules read Firestore, so the first deploy asks to let Storage read Firestore.
- Storage: a profile photo can be read by any verified user and changed or deleted only by its user; it must be an image under 5 MB, and no other file is allowed under `users/`. The in-app camera takes photos at 1440 × 1080 with JPEG quality 80, a few hundred KB, well under this limit.
- Storage: condition photos are read and added only by the rental's two participants while it is Accepted, as images under 10 MB; they are never replaced or deleted.
- Not enforced yet: the 20-photo cap and the pickup day of condition photos (checked in the app); a condition photo's `url` pointing at its own file in Storage (the rules only require a string, so a modified client could point it at any image); locking a listing's edit and delete while a rental of it is accepted or active; photo URLs pointing at this listing's Storage folder; a profile's `photoUrl` pointing at its own photo in Storage; new photos in folders with no listing (any verified user can still add up to 10 under a made-up or deleted id); a limit on the requests one borrower sends for a listing (left to `requestRental`); keeping drafts private: any verified user can read every listing, so someone else's profile downloads and caches their drafts and only leaves them out on the phone (#308).

## Not decided yet — ask before implementing

- Security rules for collections not listed in the schema above
- Cloud Function names and signatures, apart from `geocodeListingAddresses` (see "Firestore schema") and the negotiation's: `makeOffer({rentalId, deposit, pickupAddress, returnAddress})`, `respondToOffer({rentalId, action, deposit})` (`action` is `ACCEPT`, `DECLINE` or `COUNTER`; `deposit` only for `COUNTER`), `declineRequest({rentalId})`, `serverToday()` (returns `{today: "YYYY-MM-DD", now: epoch ms}`, `today` in Europe/Zurich), the scheduled `expireOffers`, the dispute's: `proposeSplit({rentalId, lenderPercent})`, `acceptSplit({rentalId, lenderPercent})` and `escalateDispute({rentalId})`, and the wallet's: `reserveDeposit({rentalId})` and `payLender({rentalId})`
- Whether the auto-cancel waits until a set time on the pickup day (e.g. noon) so offline return scans can sync first
- What happens when nobody shows up on the pickup day: condition photos are refused from the next day and cancelling closed the day before, so the rental stays Accepted with the price and the deposit held
- The exact list of push notifications
