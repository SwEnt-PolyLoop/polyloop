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
| Chat | ChatRepository | Firestore |
| Listing | ListingRepository | Firestore, Cloud Storage |
| Rental | RentalRepository | Firestore, Cloud Functions, Local upload queue → Cloud Storage (mid-rental photos) |
| Handover | HandoverRepository | Local upload queue → Cloud Storage, Cloud Functions |
| Dispute | DisputeRepository | Firestore, Cloud Storage, Cloud Functions |
| Wallet | WalletRepository | Firestore, Cloud Functions |
| Location | LocationRepository | The phone's GPS |
| Connectivity | ConnectivityRepository | The phone's network status |

## ViewModels: one per screen

Each screen has its own ViewModel, which holds that screen's state and calls the repositories it needs through their interfaces. ViewModels never call each other, and there is no use-case layer for now. Every ViewModel may read the signed-in user from AuthRepository and the online/offline status from ConnectivityRepository (for the offline banner and the actions disabled offline); that is not repeated below.

| Screen | ViewModel | Repositories it uses |
| --- | --- | --- |
| Sign-up / login | AuthViewModel | Auth; Profile (creates the profile on the first verified sign-in) |
| Profile & reviews | ProfileViewModel | Profile; Listing ("My listings"); Auth ("Sign out") |
| Wallet | WalletViewModel | Wallet |
| Chat list | ChatListViewModel | Chat; Profile (the other person); Listing (the item) |
| Chat | ChatViewModel | Chat; Rental (link to Rental detail, sending disabled during a dispute); Profile (the other person); Listing (the item) |
| Browse | BrowseViewModel | Listing; Location (distance and map position) |
| Listing detail | ListingDetailViewModel | Listing; Rental (booked days, sending the request); Profile (the owner's name and rating) |
| Create / edit listing | EditListingViewModel | Listing; Rental (editing locked while a rental is accepted or active) |
| My rentals | MyRentalsViewModel | Rental; Listing (the item); Profile (the other person) |
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

Firestore security rules must reject client writes to those fields.

## Rental statuses

Requested → Accepted → Active → Returned → Closed, plus Declined, Cancelled, Overdue, Lost, Disputed, Frozen.

## Product rules

**Accounts**
- Only @epfl.ch emails, checked with a regex, plus Firebase email verification. Name asked at sign-up; photo added later in Profile.
- Admins are identified by a Firebase custom claim.

**Money (PolyPoints)**
- In-app currency: PolyPoints (PP), 1 CHF = 1 PP. Recharge with any CHF amount on the Wallet screen. Payments are simulated. No withdrawals.
- Sending a request reserves the rental price (price per day × days) from the borrower's balance; the request fails if the balance is too low.
- The deposit is negotiated in chat before acceptance. The lender enters the agreed deposit when accepting; it is reserved then, and acceptance fails if the balance is too low. The item value is shown as the suggested deposit.

**Listings and booking**
- Price per day. The lender sets an availability window; the borrower picks dates inside it with a date picker.
- 3 to 10 listing photos, taken with the in-app camera only (no gallery).
- Pickup and drop-off addresses are typed as text; a Cloud Function geocodes them when the listing is saved. Only the area is public; the exact address is shown after acceptance.
- A 1-day buffer follows every booking. Booked days and buffer days are not selectable.
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

Packages: `model/` (data and repositories), `ui/` (screens and their ViewModels), `ui/theme/` (theme). ViewModels depend on repository interfaces and never import Firebase.

| Area | Choice |
| --- | --- |
| Architecture | MVVM without a domain layer: 13 screen ViewModels, each calling the repositories it needs |
| UI state | Each ViewModel exposes its screen state as a `StateFlow`, collected with `collectAsStateWithLifecycle()`; user actions are method calls (unidirectional data flow) |
| Navigation | Single activity, Navigation 2 (`navigation-compose`). Navigation 3 is Google's newer option, not adopted for now |
| Dependency injection | Manual constructor injection: repositories and ViewModels receive their dependencies in the constructor (ViewModels through a `ViewModelProvider.Factory`), so tests can pass fakes |
| Server logic | Cloud Functions are the only writers of rental status, deposits and balances |
| Offline cache | Firestore's built-in offline persistence |
| Upload queue | WorkManager, for offline photos and QR scans |
| Camera and QR | CameraX for photos; ML Kit code scanner to scan, ZXing to generate QR codes |
| Maps | Google Maps SDK in the app |
| Geocoding | Cloud Function, when a listing is saved |
| "Near me" | Geohash queries with the GeoFire utilities |
| LLM | Gemini API, called only from Cloud Functions (dispute proposals, court-ruling reading) |
| Scheduled jobs | Hourly Cloud Function: 30-day escalation, deadline reminders, late fees, lost items, auto-cancelling rentals blocked by a late return |
| Notifications | FCM |
| Admins | Firebase custom claim |

## Not decided yet — ask before implementing

- Firestore collections, document fields and security rules (schema not agreed)
- Cloud Function names and signatures
- Whether the auto-cancel waits until a set time on the pickup day (e.g. noon) so offline return scans can sync first
- The exact list of push notifications
- Navigation route names and their arguments
