# PolyLoop architecture

## Layers

```
UI layer        Compose screens (13 screens, see SCREENS.md)
                  │  observe state / send user actions
ViewModel layer 8 feature ViewModels (one per feature)
                  │  call directly (no domain layer)
Data layer      8 feature repositories + local upload queue (WorkManager)
                  │
Firebase        Auth · Firestore · Cloud Storage · Cloud Functions · FCM
External        Google Maps Platform (Maps SDK in app, Geocoding API from Functions) · Gemini API (from Functions)
```

## Feature slices

| Feature | Screens | ViewModel | Repository talks to |
| --- | --- | --- | --- |
| Auth | Sign-up / login | AuthViewModel | Firebase Auth |
| Profile | Profile & reviews | ProfileViewModel | Firestore, Cloud Storage |
| Chat | Chat list, Chat | ChatViewModel | Firestore |
| Listing | Browse, Listing detail, Create / edit listing | ListingViewModel (also calls RentalRepository to send a request) | Firestore, Cloud Storage |
| Rental | My rentals, Rental detail | RentalViewModel | Firestore, Cloud Functions, Local upload queue → Cloud Storage (mid-rental photos) |
| Handover | Handover (pickup + return) | HandoverViewModel | Local upload queue → Cloud Storage, Cloud Functions |
| Dispute | Dispute, Admin court ruling | DisputeViewModel | Firestore, Cloud Storage, Cloud Functions |
| Wallet | Wallet | WalletViewModel | Firestore, Cloud Functions |

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
| Architecture | MVVM without a domain layer: 8 feature ViewModels, each calling its own repository |
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
- Navigation library and route names
