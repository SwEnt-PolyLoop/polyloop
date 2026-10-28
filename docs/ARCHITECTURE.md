<!-- Edited with Claude. -->
# PolyLoop architecture

## Layers

```
UI layer        Compose screens (13 screens, see SCREENS.md)
                  │  observe state / send user actions
ViewModel layer 13 screen ViewModels (one per screen)
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
| Handover | HandoverRepository | Local upload queue → Cloud Storage, Cloud Functions |
| Dispute | DisputeRepository | Firestore, Cloud Storage, Cloud Functions |
| Wallet | WalletRepository | Firestore, Cloud Functions |
| Location | LocationRepository | The phone's GPS |
| Connectivity | ConnectivityRepository | The phone's network status |

`model/RepositoryProvider` creates the repositories, so the whole app shares one instance of each. Each repository is created on first use, so Firebase is not touched until it is needed. Tests can replace a repository with a fake by assigning it before setting content, then call `reset()`. It holds the repositories implemented so far (Auth, Listing, Profile, Chat, Rental, Connectivity); add each new repository to it when it is implemented. It also exposes the shared `FirebaseStorage` instance, for the repositories that upload photos and PDFs. Repositories that read the phone (Connectivity, later Location) need the app context: `PolyLoopApplication` passes it to `RepositoryProvider.init` at startup.

## ViewModels: one per screen

Each screen has its own ViewModel, which holds that screen's state and calls the repositories it needs through their interfaces. ViewModels never call each other, and there is no use-case layer for now. Every ViewModel may read the signed-in user from AuthRepository and the online/offline status from ConnectivityRepository (for the offline banner and the actions disabled offline); that is not repeated below.

| Screen | ViewModel | Repositories it uses |
| --- | --- | --- |
| Sign-up / login | AuthViewModel | Auth; Profile (creates the profile on the first verified sign-in) |
| Profile & reviews | ProfileViewModel | Profile; Listing ("My listings"); Auth ("Sign out") |
| Wallet | WalletViewModel | Wallet |
| Chat list | ChatListViewModel | Chat (the item and the other person are copied on each rental, see "Firestore schema") |
| Chat | ChatViewModel | Chat; Rental (link to Rental detail, sending disabled during a dispute); Profile (the other person); Listing (the item) |
| Browse | BrowseViewModel | Listing; Location (distance and map position) |
| Listing detail | ListingDetailViewModel | Listing (the item and its booked days); Rental (sending the request); Profile (the owner's name and rating); Auth (the borrower sending the request) |
| Create / edit listing | CreateListingViewModel | Listing; Rental (editing locked while a rental is accepted or active); Auth (the owner publishing the listing) |
| My rentals | MyRentalsViewModel | Rental (the item, the other person and the areas are copied on each rental, see "Firestore schema") |
| Rental detail | RentalDetailViewModel | Rental (status, actions, deposit, meeting point, mid-rental photos); Listing (the item); Profile (the other person, the review prompt) |
| Handover | HandoverViewModel | Handover; Rental (phase, role, deposit held); Dispute ("Flag damage") |
| Dispute | DisputeViewModel | Dispute; Handover (pickup and return photos); Rental (the deposit); Listing (the item) |
| Admin court ruling | AdminCourtRulingViewModel | Dispute; Handover (the photos); Rental (the deposit); Listing (the item); Profile (both parties) |

If a ViewModel needs a repository that is not listed here, add it to this table in the same pull request.

## Core rule: the app expresses intent, Cloud Functions decide

The app writes only content it owns (listings, profile fields, chat messages, photos). Anything that moves money or changes who holds an item is done by Cloud Functions, called from repositories:

- rental status changes (request, accept, decline, cancel, pickup, return, close)
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
- In-app currency: PolyPoints (PP), 1 CHF = 1 PP. Recharge with any CHF amount on the Wallet screen. Payments are simulated. No withdrawals.
- Sending a request reserves the rental price (price per day × days) from the borrower's balance; the request fails if the balance is too low.
- The deposit is negotiated in chat before acceptance. The lender enters the agreed deposit when accepting; it is reserved then, and acceptance fails if the balance is too low. The item value is shown as the suggested deposit.

**Listings and booking**
- Price per day. The lender sets an availability window; the borrower picks dates inside it with a date picker.
- 3 to 10 listing photos, taken with the in-app camera only (no gallery).
- Pickup and drop-off addresses are typed as text; a Cloud Function geocodes them when the listing is saved. Only the area is public; the exact address is shown after acceptance.
- A 1-day buffer comes before and after every booking. Booked days and buffer days are not selectable.
- Accepting a request auto-declines other requests whose dates overlap (buffer included).
- Your own listings never appear in Browse. Categories are a hardcoded list: Electronics, Sports & outdoor, Books & course material, Tools & DIY, Kitchen & home, Music, Other.

**Cancelling**
- Either party can cancel until the day before pickup. On the pickup day the rental is locked.
- If the item is still not back from the previous rental on the pickup day, the rental is auto-cancelled and the borrower gets the reserved price and deposit back.

**Handover**
- QR scan at pickup and at return. The person receiving the item scans: at pickup the lender shows the code and the borrower scans; at return the borrower shows and the lender scans.
- Each handover needs 3 to 20 condition photos; the scan is disabled until 3 are taken. Photos can also be taken mid-rental from Rental detail.
- Offline: photos and QR scans go into the local upload queue and sync later. The server trusts the app's order (a scan is accepted even if its photos are still uploading).
- After the return scan, the lender chooses "Item is fine" (full deposit released) or "Flag damage" (opens a dispute).

**Late returns**
- 3-day grace period after the deadline, with reminders. Each late day costs the price per day + 2 PP, taken from the deposit and paid to the lender. Fees stop when the deposit is used up. After 3 days the item is Lost and the rest of the deposit goes to the lender.

**Disputes**
1. Direct settlement: parties propose splits in percent; accept or counter. Auto-escalates after 30 days without agreement.
2. LLM proposal: a Cloud Function sends pickup and return photos to Gemini and gets a split + reasoning. Both parties accept or reject.
3. Court: if either rejects, the deposit is frozen. Either party uploads the court ruling PDF; Gemini pre-fills the split; an admin reviews it on the Admin court ruling screen and applies or rejects it.
- Chat is disabled while a dispute is open. Reviews (1–5 stars, no text for now) are prompted when a rental closes, however it closed.

## Technical choices

Packages: `model/` (data and repositories), `ui/` (screens and their ViewModels), `ui/theme/` (theme). ViewModels depend on repository interfaces and never import Firebase. New Firestore repositories use the shared helpers in `model/firestore/FirestoreHelpers.kt` (reading fields, errors, write timeout, listening to a query) instead of copying them; Listing, Profile and Chat will move to them.

Cloud Functions live in `functions/`, a separate TypeScript project (Node 22, `firebase-functions` v2, Jest). `src/index.ts` exports every function and sets the region, `europe-west1`, next to the Firestore database (`eur3`). In `functions/`: `npm ci` installs, `npm run lint` type-checks, `npm test` runs the tests, and `firebase deploy --only functions` deploys (needs the Blaze plan). CI runs the install, lint, build and test steps in its own job.

| Area | Choice |
| --- | --- |
| Architecture | MVVM without a domain layer: 13 screen ViewModels, each calling the repositories it needs |
| UI state | Each ViewModel exposes its screen state as a `StateFlow`, collected with `collectAsStateWithLifecycle()`; user actions are method calls (unidirectional data flow) |
| Navigation | Single activity, Navigation 2 (`navigation-compose`). Navigation 3 is Google's newer option, not adopted for now |
| Dependency injection | Manual constructor injection: repositories and ViewModels receive their dependencies in the constructor (ViewModels through a `ViewModelProvider.Factory`), so tests can pass fakes. `RepositoryProvider` creates the shared repository instances |
| Server logic | Cloud Functions (TypeScript, in `functions/`, region `europe-west1`) are the only writers of rental status, deposits and balances |
| Offline cache | Firestore's built-in offline persistence |
| Upload queue | WorkManager, for offline photos and QR scans |
| Camera and QR | CameraX for photos; ML Kit code scanner to scan, ZXing to generate QR codes |
| Maps | Google Maps SDK in the app |
| Geocoding | Cloud Function, when a listing is saved |
| "Near me" | Geohash queries with the GeoFire utilities |
| LLM | Gemini API, called only from Cloud Functions (dispute proposals, court-ruling reading) |
| Scheduled jobs | Every 5 minutes: deleting accounts not verified in time (`deleteExpiredSignUpsEvery5Minutes`). Hourly Cloud Function: 30-day escalation, deadline reminders, late fees, lost items, auto-cancelling rentals blocked by a late return |
| Notifications | FCM |
| Admins | Firebase custom claim |

## Firestore schema

Dates without a time are stored as `YYYY-MM-DD` strings, prices as whole PolyPoints. Date-times (`createdAt`, `lastMessageAt`, `sentAt`) are Firestore Timestamps, set with `FieldValue.serverTimestamp()`; reads use `ServerTimestampBehavior.ESTIMATE`, because until the server confirms a write a listener otherwise sees these fields as null. When a new field is added to an existing collection, reading must give it a default value: documents written before it do not have it.

| Path | Fields | Notes |
| --- | --- | --- |
| `users/{uid}` | `name`, `email`, `photoUrl`, `averageRating`, `reviewCount` | Missing fields read as `""` or 0. |
| `listings/{listingId}` | `ownerId`, `title`, `description`, `category`, `photoUrls`, `pricePerDay`, `itemValue`, `availableFrom`, `availableTo`, `status`, `pickupArea`, `dropOffArea`, `bookedRanges` | Public. `bookedRanges` is a list of `{start, end}` (`YYYY-MM-DD`, both days booked, the buffer days around it not included); missing reads as an empty list. It holds accepted rentals only: a pending request blocks no day. Each range copies the `startDate` and `endDate` of an accepted rental, which stay in `rentals/{rentalId}` to tell who has the item and when; the copy on the listing is public so that anyone can see which days are free without reading other people's rentals. Only Cloud Functions write it; the app never does, so it stays empty until the Function that accepts rentals exists. |
| `listings/{listingId}/private/addresses` | `pickup`, `dropOff` | Exact addresses, readable only by the owner. |
| `rentals/{rentalId}` | `listingId`, `listingTitle`, `listingPhotoUrl`, `ownerId`, `ownerName`, `borrowerId`, `borrowerName`, `participants`, `startDate`, `endDate`, `status`, `pricePerDay`, `totalPrice`, `pickupArea`, `dropOffArea`, `pickupAddress`, `returnAddress`, `createdAt`, `lastMessage`, `lastMessageAt` | See below. |
| `rentals/{rentalId}/messages/{messageId}` | `senderId`, `text`, `sentAt`, `type` (`USER` or `SYSTEM`) | One conversation per rental. |

Rentals:
- The listing title, both names and the public areas are copied when the rental is created, so My rentals and the chat list need no other read. A missing owner name is stored as `""`; screens show a translated placeholder.
- `participants` is `[ownerId, borrowerId]`; the chat list queries it with `array-contains` only and sorts by `lastMessageAt` in the app, so it needs no composite index.
- My rentals queries `borrowerId` or `ownerId` only and sorts newest first in the app (a user has few rentals), so it needs no index.
- `lastMessageAt` is set to the same server timestamp as `createdAt` when the rental is created: the chat list sorts by it, and leaves out a rental that lacks it.
- `pickupAddress` and `returnAddress` stay empty until the lender accepts; the borrower cannot read the listing's exact addresses before that.
- **Temporary exception to the core rule:** until the `requestRental` Cloud Function exists, the app creates the rental itself, with status `REQUESTED` only and no money reserved. The security rules allow only that creation, and afterwards only `lastMessage` and `lastMessageAt` can be changed by the participants. Until then, the dates are checked against `bookedRanges` only in the app (Listing detail reads them again from the server just before sending), so a client that skips this check is not stopped.

Listing photos uploaded by the app go to Cloud Storage under `listings/{listingId}/`, so the Storage rules can check the owner. Photos added before this rule live in `listing_images/` and keep working through their download URLs.

## Security rules

`firestore.rules` and `storage.rules` are listed in `firebase.json`, so the emulators enforce them; `FirestoreRulesTest` and `StorageRulesTest` (in `androidTest/.../rules/`) check them. Every read and write needs a verified `@epfl.ch` account. Beyond the notes in the schema above:
- A listing must pass the same checks as `Listing.canPublish` (3 to 10 photo URLs, all strings, prices from 1 to 99,999 PP, `availableFrom` before `availableTo`, a known category and status). Clients never write `bookedRanges` (a Cloud Function will, on acceptance) and never change `ownerId`. They create `pickupArea` and `dropOffArea` empty, never write `pickupPoint` or `dropOffPoint`, and never change any of the four afterwards (`geocodeListingAddresses` does). A listing cannot be created at an id whose `private/addresses` is left over from a deleted listing.
- A rental request must match its listing, which must be `PUBLISHED`: same owner, title, first photo (`""` if the listing has none), price per day and areas, dates inside the availability window, and `totalPrice` equal to the price per day × the number of days.
- Profiles: a user creates and edits only their own, with their own email; `averageRating` and `reviewCount` are left to Cloud Functions.
- Storage: listing photos must be images under 100 MB (a placeholder until the camera sets the photo size). They are uploaded before the listing document exists, so a folder whose listing does not exist yet is open to any verified user; once it exists, only the owner may change it. These rules read Firestore, so the first deploy asks to let Storage read Firestore.
- Not enforced yet: locking a listing's edit and delete while a rental of it is accepted or active; photo URLs pointing at this listing's Storage folder; Storage writes to folders with no listing (any verified user, so made-up and deleted ids too); a limit on the requests one borrower sends for a listing (left to `requestRental`).

## Not decided yet — ask before implementing

- Security rules for collections not listed in the schema above
- Cloud Function names and signatures
- Whether the auto-cancel waits until a set time on the pickup day (e.g. noon) so offline return scans can sync first
- The exact list of push notifications
