<!-- Edited with Claude. -->
# PolyLoop screen specification

One section per screen: what it shows and what it lets the user do. Mock-ups: https://claude.ai/artifact/FH2RZaMrYTGYU2fNz6UpZc

## Conventions

PolyLoop has 13 screens, each backed by its own ViewModel, which uses the repositories it needs (see the table in ARCHITECTURE.md). Each section below lists what a screen shows and what it lets the user do.

Elements shared across screens:

- **Navigation.** Users move between screens through buttons on the screens themselves and through the top-bar menu.
- **Top bar.** Transparent, with a menu button and a profile button. The menu expands to list every standalone screen: Browse, Create listing, My rentals, Chat list, Wallet, Profile, and Admin court ruling for admins. Screens tied to one listing or rental open from inside the app, not from the menu. Rental screens also show a role pill, “You lend” or “You borrow”.
- **Currency.** All prices and deposits are in PolyPoints, PolyLoop's in-app currency, at 1 CHF = 1 PolyPoint. Users recharge on the Wallet screen. Recharging is simulated: no real payment is made.
- **Offline banner.** Shown at the top of every screen while offline. Actions that need a connection (browsing new listings, recharging, payments, sending messages) stay visible but disabled.
- **Locations offline.** Map tiles need a connection, so offline screens show meeting points as text (the address).
- **Photos.** Every photo is taken with the in-app camera, never picked from the gallery.

## Sign-up / login

Only users with a verified EPFL email get past this screen. ViewModel: AuthViewModel, using AuthRepository and ProfileRepository.

- Email and password fields, with a switch between “Sign up” and “Log in”.
- Only @epfl.ch addresses are accepted. A regex check shows an inline error as soon as the address doesn't match.
- A name field when signing up. The photo is added later in Profile.
- After sign-up, a “Check your inbox” state with a “Resend email” button and an “I've verified” button that refreshes the account.
- A “Forgot password” link.

## Profile & reviews

The profile shows who someone is and how past rentals went. It opens from the profile button in the top bar or by tapping a person on Listing detail or Rental detail. ViewModel: ProfileViewModel, using ProfileRepository, ListingRepository and AuthRepository.

- Name, photo, and the average rating (1 to 5 stars) with the number of reviews.
- The list of reviews, each with stars, author, date and the item rented.
- On your own profile: a “My listings” section with the items you have put up for rent (available offline), plus “Edit profile” (where the photo is added) and “Sign out”.
- A review prompt appears as soon as a rental closes, however it closed: clean return, direct settlement, LLM proposal or court ruling. Like rating a ride in Uber or Bolt, each participant rates the other once, from 1 to 5 stars. Stars only for now; a written comment may come later.
- Other people's profiles don't show their listings for now.

## Wallet

The Wallet screen holds the user's in-app balance, which pays for rentals and deposits. ViewModel: WalletViewModel, using WalletRepository. The balance is only ever changed by Cloud Functions; the app sends a request and shows the result.

- The available balance in PolyPoints, plus the amount currently held as deposits.
- A “Recharge” button: the user enters any Swiss franc amount and receives the same number of PolyPoints. Recharging is simulated; no real payment is made.
- A transaction history, newest first: recharges, rental payments, late fees, and deposits held, released or split.
- PolyPoints cannot be withdrawn back to Swiss francs.
- Offline: balance and history stay readable from cache; recharging is disabled.

## Chat list & chat

Each rental has exactly one 1:1 conversation between its lender and borrower. ViewModels: ChatListViewModel, using ChatRepository, ProfileRepository and ListingRepository; ChatViewModel, using ChatRepository, RentalRepository, ProfileRepository and ListingRepository.

- **Chat list.** A separate screen lists all the user's conversations, one per rental, with the other person, the item and the last message.
- The chat opens as soon as a request is sent, so both sides can agree on details before acceptance, such as the meeting point and the deposit.
- Header with the other person's name, the item, and a link back to Rental detail.
- Messages with timestamps, newest at the bottom, and a text field with a send button.
- A push notification for each new message.
- Offline: the chat list and the most recent messages stay readable from cache; the send button is disabled.
- No chat during a dispute: sending is disabled while a dispute is open.
- Messages are text only.

## Browse

Browse shows available items near the user, as a map or a list. ViewModel: BrowseViewModel, using ListingRepository and LocationRepository.

- A toggle between map view (Google Maps SDK) and list view.
- Map pins sit at each listing's pickup area, never the exact address.
- List sorted by distance, using the phone's GPS.
- Listing cards with the first photo, title, price per day in PolyPoints, distance and pickup area. Tapping a card opens Listing detail.
- A search bar and filters, such as category and price.
- Search: every word typed must appear in the title or the description, ignoring case, accents, hyphens and punctuation. Matches in the title rank first; otherwise the distance order is kept. Several categories can be selected at once, and the price range bounds are inclusive.
- The user's own listings never appear here.
- Offline: browsing new listings needs a connection, so the screen shows the offline banner and an empty state.
- Categories are a fixed list: Electronics, Sports & outdoor, Books & course material, Tools & DIY, Kitchen & home, Music, Other.

## Listing detail

Listing detail is where a borrower decides and sends a request. ViewModel: ListingDetailViewModel, using ListingRepository, RentalRepository (to send the request) and ProfileRepository.

- Photo carousel, title, description, price per day, and the suggested deposit (the item's value), all in PolyPoints. The final deposit is negotiated in chat; the lender enters it when accepting.
- The owner's name and rating, linking to their profile.
- A small map of the pickup area, placed by geocoding the typed address. The exact address only appears on Rental detail after acceptance.
- The lender's availability window, and a “Request” button that opens a date picker limited to that window. Booked days, plus a 1-day buffer after each booking, are greyed out. The total is the price per day times the number of days. Disabled offline.
- Sending a request reserves the rental price from the borrower's balance; if the balance is too low, the request does not go through.
- On your own listing: “Edit” and “Delete” instead of “Request”, locked while a rental is accepted or active.

## Create / edit listing

This form puts an item up for rent, or changes an existing listing. ViewModel: EditListingViewModel, using ListingRepository and RentalRepository.

- A photo row with an “Add photo” button that opens the in-app camera. Each listing needs 3 to 10 photos, which can be removed and reordered.
- Fields: title, description, category, price per day, item value (the suggested deposit), the availability window (the dates the lender is willing to lend), and the pickup and drop-off addresses, typed as text.
- The typed addresses are turned into map coordinates by geocoding.
- “Publish” when creating, “Save” when editing. Uploads need a connection.
- Editing is locked while a rental of the item is accepted or active.

## My rentals

My rentals is the user's overview of everything they borrow and lend, and it works fully offline. ViewModel: MyRentalsViewModel, using RentalRepository, ListingRepository and ProfileRepository.

- Two tabs: “Borrowing” and “Lending”.
- Each row shows the item, the other person, a status label, the return deadline, and the meeting point as text.
- Incoming requests appear at the top of “Lending”. Tapping any row opens Rental detail.
- Offline: the whole list, deadlines and meeting points stay available from cache.
- The items the user has put up for rent are listed in Profile, not here.

## Rental detail

Rental detail is the hub for one rental: what it is, where it stands, and the next action. ViewModel: RentalDetailViewModel, using RentalRepository, ListingRepository and ProfileRepository.

- Item, dates, return deadline, the other person (linking to their profile), and a status label.
- The deposit, entered by the lender when accepting, and its state: held, released, or frozen.
- The meeting point: the lender sets the pickup and drop-off addresses, after agreeing with the borrower in chat if needed. Shown on a map with a “Navigate” button; offline, as text.
- A “Chat” button.
- A “Take photo” button while the rental is active, for condition photos between pickup and return.
- The main action, which changes with status and role:
    - Requested, lender: “Accept” (entering the deposit, which is then reserved from the borrower's balance; acceptance fails if it is too low) or “Decline”. Accepting automatically declines other requests whose dates overlap, counting the 1-day buffer between rentals.
    - Accepted: “Start pickup”, which opens Handover.
    - Active: “Start return”, which opens Handover.
    - Disputed: “View dispute”, which opens Dispute.
    - Closed: the review prompt (see Profile & reviews).
- Either party can cancel until the day before pickup. On the pickup day the rental is locked and can no longer be cancelled. The 1-day buffer between rentals gives the lender time to cancel the next rental if the previous one went wrong. If the item is still not back from the previous rental on the pickup day, the rental is cancelled automatically and the borrower gets the reserved price and the deposit back.
- **Late returns.** After the deadline there is a 3-day grace period with daily reminders. Each late day deducts the price per day plus a fixed fee of 2 PolyPoints from the held deposit and pays it to the lender, so returning late always costs more than renting longer. Fees stop once the deposit is used up. After 3 days the item counts as lost and whatever remains of the deposit goes to the lender.

## Handover

One Handover screen covers both pickup and return, and adapts to the phase and to the user's role. ViewModel: HandoverViewModel, using HandoverRepository, RentalRepository and DisputeRepository. Mock-ups: [PolyLoop Handover screen](https://claude.ai/artifact/FH2RZaMrYTGYU2fNz6UpZc).

- A progress strip: Pickup → In use → Return.
- **Condition photos.** In-app camera, each photo labelled with its phase and time. Photos taken offline show a “Not synced” badge. Each handover needs at least 3 and at most 20 photos. Photos can also be taken mid-rental from Rental detail.
- **QR code.** The person receiving the item scans:
    - At pickup, the lender shows the code and the borrower scans it.
    - At return, the borrower shows the code and the lender scans it.
- The scan button stays disabled until 3 photos are taken.
- After the return scan, the lender sees the pickup and return photos side by side, with two buttons:
    - “Item is fine” releases the full deposit to the borrower.
    - “Flag damage” opens a dispute and keeps the deposit held.
- A footer showing the deposit held.
- Offline: photos and scans go into the local upload queue and sync on reconnect. The server trusts the app's order and accepts a scan even if its photos are still uploading.

## Dispute

The Dispute screen walks both parties through the three settlement stages until the deposit is split. ViewModel: DisputeViewModel, using DisputeRepository, HandoverRepository, RentalRepository and ListingRepository.

- A header with the item, the deposit amount, and the current stage.
- Pickup and return photos side by side, as evidence.
- **Stage 1, direct settlement.** Each party proposes a split as a percentage of the deposit (for example 70% to the lender, 30% to the borrower). The other can accept it or counter. The history of proposals is visible. An “Escalate” button moves to stage 2, and escalation happens automatically after 30 days without agreement.
- **Stage 2, LLM proposal.** A card with the proposed split and the LLM's reasoning, plus “Accept” and “Reject” for each party. If both accept, the split is applied and the rental closes.
- **Stage 3, court ruling.** If either party rejects, the deposit stays frozen. Either party can then upload the court ruling as a PDF. The screen shows its status: “Waiting for review”, then applied or rejected by an admin.
- The rental chat is disabled while the dispute is open.

## Admin court ruling

This admin-only screen checks uploaded court rulings before they move any money. ViewModel: AdminCourtRulingViewModel, using DisputeRepository, HandoverRepository, RentalRepository, ListingRepository and ProfileRepository. Admins are identified by a custom claim on their Firebase account.

- A queue of uploaded rulings waiting for review, each with the item, both parties, the deposit, and the upload date.
- A detail view with the ruling PDF, the photos, the proposal history, and the LLM's earlier proposal.
- The split read from the PDF by Gemini, pre-filled in percent and editable by the admin.
- “Apply ruling” behind a confirmation step, since this cannot be undone. Applying it splits the deposit and closes the rental.
- “Reject” for an invalid or unreadable document; the uploader is notified and can upload again.
