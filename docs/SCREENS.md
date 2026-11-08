<!-- Edited with Claude. -->
# PolyLoop screen specification

One section per screen: what it shows and what it lets the user do. Mock-ups: https://claude.ai/artifact/FH2RZaMrYTGYU2fNz6UpZc

## Conventions

PolyLoop has 15 screens, each backed by its own ViewModel, which uses the repositories it needs (see the table in ARCHITECTURE.md). Each section below lists what a screen shows and what it lets the user do.

Elements shared across screens:

- **Navigation.** Users move between screens through buttons on the screens themselves and through the navigation drawer.
- **Top bar.** Transparent, with a menu button and a profile button. Rental screens also show a role pill, “You lend” or “You borrow”.
- **Navigation drawer** (Figma frame 03). The menu button opens it; Back, a tap outside or a swipe closes it. A header with the signed-in user's avatar (photo, or their initial), name and rating, then every standalone screen with an icon, the current one highlighted: Browse, Create listing, My rentals, Chats, Wallet, Profile. After a divider, “Court rulings” with an “Admin” badge, for admins only. A footer notes that screens tied to one listing or rental open from inside the app, not from the drawer. Tapping an item opens it and closes the drawer. ViewModel: DrawerViewModel, using ProfileRepository.
- **Currency.** All prices and deposits are in PolyPoints, PolyLoop's in-app currency, at 1 CHF = 1 PolyPoint. Users recharge on the Wallet screen. Recharging is simulated: no real payment is made.
- **Offline banner.** Shown at the top of every screen while offline. Actions that need a connection (browsing new listings, recharging, payments, sending messages) stay visible but disabled.
- **Locations offline.** Map tiles need a connection, so offline screens show meeting points as text (the address).
- **Photos.** Every photo is taken with the in-app camera, never picked from the gallery.

## Sign-up / login

Only users with a verified EPFL email get past this screen. ViewModel: AuthViewModel, using AuthRepository and ProfileRepository.

- Email and password fields, with a switch between “Sign up” and “Log in”.
- Only @epfl.ch addresses are accepted. A regex check shows an inline error as soon as the address doesn't match.
- When signing up, the password must have at least 8 characters, one uppercase letter, one lowercase letter, one number (0–9) and one special character among `` ^ $ * . [ ] { } ( ) ? " ! @ # % & / \ , > < ' : ; | _ ~ ` `` (other symbols, spaces and emojis do not count). As soon as the password is typed, a “Your password needs:” list appears under the field, with one bullet per broken rule; each bullet disappears once its rule is met, and the list disappears with the last one. Pressing “Sign up” does not create the account until every rule is met. A password over 4096 characters, the most the Firebase password policy accepts, gets “Your password is too long.” If the policy still refuses the password, the form shows “Your password is too weak.” Logging in does not check these rules.
- A name field when signing up. The photo is added later in Profile.
- After sign-up, or when logging in with an EPFL email that isn't verified yet, a “Check your inbox” state with a “Resend email” button, an “I've verified” button that refreshes the account, and a way back to the form to fix a mistyped email. A non-EPFL email gets an error on the form and never reaches this state.
- “Resend email” is disabled for 60 seconds after each email sent, with a countdown.
- “Check your inbox” says the email must be verified within 5 minutes of creating the account, and that resending does not give more time. An account not verified by then is deleted, so “I've verified” and “Resend email” then say the sign-up expired and the user should go back to the form and sign up again.
- A “Forgot password” link.
- The first verified log-in creates the user's profile. If the name could not be saved at sign-up, a “What's your name?” step asks for it first. If the profile cannot be created, the user is signed out and the log-in form shows an error, so they can try again.

## Profile & reviews

The profile shows who someone is and how past rentals went. It opens from the profile button in the top bar or by tapping a person on Listing detail or Rental detail. ViewModel: ProfileViewModel, using ProfileRepository, ListingRepository, RentalRepository and AuthRepository.

- Name, photo, and the average rating (1 to 5 stars) with the number of reviews. The photo is round, cropped to fill the circle; without a photo, or when it cannot be loaded, the first letter of the name takes its place.
- The list of reviews, each with stars, author, date and the item rented.
- On your own profile: “Edit profile” (where the photo is added) and “Sign out” side by side under the rating, then a “My listings” section with the items you have put up for rent.
- “My listings” is a card with the number of listings and one row per listing: first photo, title, price per day, and a pill showing where it stands right now, updated live from its rentals. The listings themselves are loaded again each time the profile comes back on screen, so a listing edited or deleted meanwhile shows as it is now. When several rentals apply, the first match wins: “Lost” (a borrower never gave the item back), “Frozen” (a dispute went to court and the deposit is frozen), “Disputed” (a dispute is open), “Overdue” (a return is late), “On loan” (the item is with a borrower), “Booked” (a request was accepted), “Requested” (a request waits for an answer), otherwise “Available”. The pills use the colours of the matching status on My rentals; “Available” uses those of a finished rental. A listing its owner unpublished (a draft) shows a plain “Draft” label, white with no colour, instead of “Available”; if one of its rentals is still under way, it shows that rental's pill instead. Tapping a row opens Listing detail. The card loads apart from the profile, with its own spinner, a generic error “Could not load your listings” with “Retry”, and the empty text “You haven't listed anything yet”. If loading them again fails, the listings loaded before stay, with the note “Could not refresh your listings. These are the last ones loaded.” under them, until a later load succeeds; if there were none, the note under the empty text is only “Could not refresh your listings.”, so a user who just listed an item knows why it is missing. Offline, the cards and their pills stay available as they were loaded before going offline.
- A review prompt appears as soon as a rental closes, however it closed: clean return, direct settlement, LLM proposal or court ruling. Like rating a ride in Uber or Bolt, each participant rates the other once, from 1 to 5 stars. Stars only for now; a written comment may come later.
- On someone else's profile, a “Listings” card under the header shows their published listings (their drafts are left out), with the number of listings and one row per listing: first photo, title and price per day, without a pill, as their rentals belong to other people and are not read. Tapping a row opens Listing detail. Like “My listings”, the card loads apart from the profile, with its own spinner, the error “Could not load the listings” with “Retry”, and the empty text “[Name] has no listings yet” (“This user has no listings yet” when they have no name). The listings are loaded again each time the profile comes back on screen; if that fails, the listings loaded before stay, with the note “Could not refresh the listings. These are the last ones loaded.”, or only “Could not refresh the listings.” under the empty text if there were none. Their listings are read from the server only, because the phone's cache may hold only some of them, so a phone that reports being online but cannot reach the server gets this error or this note too. Offline, nothing is loaded: the card shows “Listings are unavailable offline” instead, without the count or “Retry”, and loads the listings by itself when the connection comes back.

## Edit profile

Edit profile is where the signed-in user changes their name and photo. It opens from “Edit profile” on their own profile. ViewModel: EditProfileViewModel, using ProfileRepository.

- The photo, round like on the profile, with a “Take photo” button. It opens the in-app camera for a single photo (no count, it closes once the photo is taken), after asking for the camera permission; a refusal shows the same message as Create listing. The new photo replaces the old one in the circle right away, but is uploaded only on “Save”, so leaving without saving changes nothing.
- The name field. “Save” with a blank name shows “Enter your name” and saves nothing; the name is trimmed.
- “Save” uploads the new photo, if one was taken, then saves the name and the photo. While it runs, the field and both buttons are disabled and “Save” shows a spinner. On success the screen goes back to the profile, which shows the new name and photo. If it fails, an error is shown and the edits are kept; saving again does not upload the same photo a second time.
- A spinner while the profile loads, and an error with “Retry” if it cannot be loaded.

## Wallet

The Wallet screen holds the user's in-app balance, which pays for rentals and deposits. ViewModel: WalletViewModel, using WalletRepository. The balance is only ever changed by Cloud Functions; the app sends a request and shows the result.

- The available balance in PolyPoints (everything held excluded), then “Held as deposits”, the deposits held, and, only when it is above 0, “Held for rentals”, the rental prices reserved since a request was sent (the deposit only exists once an offer is accepted). Available plus both held amounts is the user's total.
- A “Recharge” button: the user enters a whole Swiss franc amount from 1 to 99,999 and receives the same number of PolyPoints. Recharging is simulated; no real payment is made.
- A transaction history, newest first: recharges, rental payments, late fees, and deposits held, released, split or forfeited (the rest of the deposit, paid to the lender once the item is lost). The amounts add up to the available balance; late fees and forfeited deposits show only in the lender's history, and a borrower's late fees appear as a smaller deposit release.
- PolyPoints cannot be withdrawn back to Swiss francs.
- Offline: balance and history stay readable from cache; recharging is disabled.

## Chat list & chat

Each rental has exactly one 1:1 conversation between its lender and borrower. ViewModels: ChatListViewModel, using ChatRepository, ProfileRepository and ListingRepository; ChatViewModel, using ChatRepository, RentalRepository, ProfileRepository and ListingRepository.

- **Chat list.** A separate screen lists all the user's conversations, one per rental, with the other person, the item and the last message.
- The chat opens as soon as a request is sent, so both sides can agree on details before acceptance, such as the meeting point and the deposit.
- **Offer card** (Figma frames 19.10 to 19.15). While an offer waits for an answer, the chat shows its amount and when it expires. The lender's offer gives the borrower “Decline”, “Counter” and “Accept”; “Counter” asks for an amount, with “Back” and “Send”. A counteroffer gives the lender “Decline” and “Accept” only. The author of the offer sees it as waiting, with no action. Each step adds a centred system message, including an accepted, declined or expired offer. The actions are disabled offline.
- Header with the other person's name, the item, and a link back to Rental detail.
- Messages with timestamps, newest at the bottom, and a text field with a send button.
- A push notification for each new message.
- Offline: the chat list and the most recent messages stay readable from cache; the send button is disabled.
- No chat during a dispute: sending is disabled while a dispute is open.
- Messages typed by users are text only.

## Browse

Browse shows available items near the user, as a map or a list. ViewModel: BrowseViewModel, using ListingRepository and LocationRepository.

- A toggle between map view (Google Maps SDK) and list view.
- Map pins sit at each listing's pickup area, never the exact address.
- List sorted by distance, using the phone's GPS.
- Listing cards with the first photo, title, price per day in PolyPoints, distance and pickup area. Tapping a card opens Listing detail.
- A search bar and filters, such as category and price.
- Search: every word typed must appear in the title or the description, ignoring case, accents, hyphens and punctuation. A word also matches the start of a word, or several words typed without spaces ("mountainbike"). Common words such as "for", "with", "pour" or "avec" are ignored. Matches in the title rank first; otherwise the distance order is kept. Several categories can be selected at once, and the price range bounds are inclusive.
- The user's own listings never appear here.
- Offline: browsing new listings needs a connection, so the screen shows the offline banner and an empty state.
- Categories are a fixed list: Electronics, Sports & outdoor, Books & course material, Tools & DIY, Kitchen & home, Music, Other.

## Listing detail

Listing detail is where a borrower decides and sends a request. ViewModel: ListingDetailViewModel, using ListingRepository, RentalRepository (to send the request) and ProfileRepository.

- Photo carousel, title, description, price per day, and the suggested deposit (the item's value), all in PolyPoints. The final deposit is negotiated after the request: the lender offers an amount, and the borrower answers in the chat.
- The owner's name and rating, linking to their profile. If the owner is someone else and their profile is already open further back (the user came from it), the link goes back to it instead of opening it again.
- A small map of the pickup area, placed by geocoding the typed address. The exact address only appears on Rental detail after acceptance. The map is a still picture that cannot be scrolled or zoomed, with a circle of about 700 m around the area's rounded point. Until the address is geocoded, and while offline, the map is replaced by the area name as text.
- The lender's availability window, and a “Request” button that opens a date picker limited to that window. Booked days (accepted rentals only; a pending request blocks no day), plus a 1-day buffer before and after each booking, are greyed out. The total is the price per day times the number of days. Disabled offline.
- When the request is sent, the booked days are read again from the server. If the dates were booked meanwhile, nothing is sent: the picker greys out the new booked days and shows “These dates are no longer available”, until the user taps a new day. If the listing was deleted or unpublished meanwhile, the picker closes and the listing shows as not found.
- Sending a request reserves the rental price from the borrower's balance; if the balance is too low, the request does not go through.
- On your own listing: “Edit” and “Delete” instead of “Request”, locked while a rental is accepted or active.

## Create / edit listing

This form puts an item up for rent, or changes an existing listing. ViewModel: CreateListingViewModel, using ListingRepository and AuthRepository.

- A photo row with an “Add photo” button that opens the in-app camera, after asking for the camera permission. If the user refuses, a message explains that photos are taken in the app and how to allow the camera in Settings; there is no gallery or system-camera fallback. Each listing needs 3 to 10 photos (taken with the in-app camera only), which can be removed and reordered. The hint showing how many photos are missing is marked as an error once the user presses “Publish” with fewer than 3.
- Fields: title, description, category, price per day, item value (the suggested deposit), the availability window (the dates the lender is willing to lend), and the pickup and drop-off addresses, typed as text.
- **In-app camera.** Full screen, over the form: a live preview, a shutter button, a close button and the count “n / 10”. It stays open after each photo so several can be taken in a row, and closes with the close button, the Back button, or by itself at 10 photos. If the camera cannot start or a photo cannot be saved, a short message is shown and the camera stays open. Photos are limited in size: shot at about 1440 × 1080 (4:3) as JPEG at quality 80.
- **Photo files.** Each photo is a file on the phone until the listing is saved. It is deleted when the user removes the photo, when the form is closed without publishing, and after the listing is published (once the photos are uploaded). If publishing fails the files are kept, so the user can try again. Leftovers from an app killed mid-form are removed a day later, once, when the camera next opens, in the background; the photos the open form still holds are never removed.
- The typed addresses are turned into map coordinates by geocoding.
- “Publish” when creating, “Save” when editing. Uploads need a connection.
- Pressing “Publish” with every field valid uploads the photos, saves the listing as published (title and description trimmed) and opens the new listing's detail, replacing the form in the navigation history so Back cannot publish it twice. While it runs the button is disabled and shows a spinner; if it fails, the error is shown above the button and the form keeps its content.
- Editing is locked while a rental of the item is accepted or active.

## My rentals

My rentals is the user's overview of everything they borrow and lend, and it works fully offline. ViewModel: MyRentalsViewModel, using RentalRepository, ListingRepository and ProfileRepository.

- Two tabs: “Borrowing” and “Lending”, each with the same two sections, “REQUESTS” and “CURRENT”. A section shows only when it has rows.
- “REQUESTS” holds pending requests only, newest first. “CURRENT” holds every other rental, what needs attention first (overdue, then disputed, frozen and lost, then active, accepted, finished, and declined or cancelled last), newest first within each status.
- Each card shows the listing photo, the item, the other person (“from [Lender name]” on Borrowing), a status label, and one “what comes next” line: the dates while requested (with the status “Offer sent” or “Offer received” while an offer waits for an answer, Figma frames 10.10 and 11.10), “Pickup 15 Oct · area” once accepted, “Return by 8 Oct · area” while active or overdue, the area while disputed, frozen or lost, and “Closed 2 Oct” once closed. Returned, declined and cancelled rentals have no line. The area is the public one; the closed date is the rental's last day for now.
- On “Lending”, a request shows the borrower, the dates and the reserved price, and only its “Review request” button reacts, which opens Review request. Once an offer exists, the card shows where it stands, and its button opens the chat instead. On “Borrowing”, tapping a request opens Rental detail. Tapping a row in “CURRENT” opens Rental detail.
- Offline: the whole list, deadlines and meeting points stay available from cache.
- The items the user has put up for rent are listed in Profile, not here.

## Review request

Review request is where the lender answers a request that has no offer yet (Figma frame 12.10). It opens from “Review request” on My rentals. ViewModel: ReviewRequestViewModel, using RentalRepository, ListingRepository and ProfileRepository.

- The item, the dates and number of days, the rental price, and the borrower with their rating.
- A deposit field, filled in with the suggested deposit (the item's value): a whole amount from 1 to 99,999 PolyPoints.
- The pickup and drop-off addresses, typed as text. The borrower sees them only once the rental is accepted.
- “Send offer”, disabled until the deposit is valid and both addresses are filled in. It sends the offer and opens the chat, where the borrower answers.
- “Decline”, which declines the request without an offer.
- While an action runs, both buttons are disabled; if it fails, the error is shown and the form keeps its content. Both are disabled offline.

## Rental detail

Rental detail is the hub for one rental: what it is, where it stands, and the next action. ViewModel: RentalDetailViewModel, using RentalRepository, ListingRepository and ProfileRepository.

So far only the Requested state is built (other statuses show the item's title and a short “not available yet” text). Its pickup area uses the same small map as Listing detail, or the same placeholder until the address is geocoded, while offline, or when the listing cannot be read.

- Item, dates, return deadline, the other person (linking to their profile), and a status label.
- The deposit, once an offer is accepted, and its state: held, released, or frozen. “Held” appears only once deposits are really reserved, which needs the wallet's Firestore implementation; until then only the amount shows.
- While an offer waits for an answer, its amount and when it expires (Figma frame 13.10).
- The meeting point: the lender sets the pickup and drop-off addresses when sending the offer, after agreeing with the borrower in chat if needed. The exact addresses show only once the rental is accepted, on a map with a “Navigate” button; offline, as text.
- A “Chat” button.
- A “Take photo” button while the rental is active, for condition photos between pickup and return.
- The main action, which changes with status and role:
    - Requested: the “Chat” button leads to the chat, where the offer is answered (see Chat list & chat). Accepting reserves the deposit from the borrower's balance, and fails if it is too low. Accepting automatically declines other requests whose dates overlap, counting the 1-day buffer between rentals, with or without an offer.
    - Accepted: “Start pickup”, shown only on the pickup day, which opens Handover.
    - Active: “Start return”, which opens Handover.
    - Disputed: “View dispute”, which opens Dispute.
    - Closed: the review prompt (see Profile & reviews).
- Either party can cancel until the day before pickup. On the pickup day the rental is locked and can no longer be cancelled. The 1-day buffer between rentals gives the lender time to cancel the next rental if the previous one went wrong. If the item is still not back from the previous rental on the pickup day, the rental is cancelled automatically and the borrower gets the reserved price and the deposit back.
- **Late returns.** After the deadline there is a 3-day grace period with daily reminders. Each late day deducts the price per day plus a fixed fee of 2 PolyPoints from the held deposit and pays it to the lender, so returning late always costs more than renting longer. Fees stop once the deposit is used up. After 3 days the item counts as lost and whatever remains of the deposit goes to the lender.

## Handover

One Handover screen covers both pickup and return, and adapts to the phase and to the user's role. ViewModel: HandoverViewModel, using HandoverRepository, RentalRepository and DisputeRepository. Mock-ups: [PolyLoop Handover screen](https://claude.ai/artifact/FH2RZaMrYTGYU2fNz6UpZc).

- The pickup opens only on the pickup day: on another day the screen shows the pickup date, and no photo can be taken.
- **Condition photos.** In-app camera, each photo labelled with its phase and time. Photos taken offline show a “Not synced” badge. Each person takes their own 3 to 20 photos per handover and sees only their own. They show 4 per row, oldest first, each with the time it was taken; tapping one shows it full screen. The camera closes after each photo, which uploads right away; a photo that fails to upload can be tapped to try again. Photos can also be taken mid-rental from Rental detail.
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

The Dispute screen walks both parties through the three settlement stages until the deposit is split. ViewModel: DisputeViewModel, using DisputeRepository, HandoverRepository (the photos, not used yet) and RentalRepository (the item, the other person and, once rentals have one (#176), the deposit; a placeholder of 100 PolyPoints until then).

- A header with the item, the deposit amount, and the current stage.
- Pickup and return photos side by side, as evidence.
- **Stage 1, direct settlement.** Each party proposes a split as a percentage of the deposit (for example 70% to the lender, 30% to the borrower). The other can accept it or counter. The history of proposals is visible. An “Escalate now” button moves to stage 2 at once; otherwise escalation happens automatically after 30 days without agreement, and the screen shows the days left.
- **Stage 2, LLM proposal.** A card with the proposed split and the LLM's reasoning, plus “Accept” and “Reject” for each party. If both accept, the split is applied and the rental closes. There is no “Escalate now” and no automatic escalation here: the dispute goes to court only if either party rejects.
- **Stage 3, court ruling.** Once the dispute reaches court, because either party rejected the LLM proposal, the deposit stays frozen. Either party can then upload the court ruling as a PDF. The screen shows its status: “Waiting for review”, then applied or rejected by an admin.
- **The proposal card.** It offers “Propose a split” when there is no proposal, “Update” on the user's own proposal while they wait for the other person, and “Counter” or “Accept” on the other person's. The user sets the split by typing a whole percentage for either party, next to the bar (the other share follows), or by dragging or tapping the bar. The bar is hidden from screen readers, whose users set the split with the percent fields.
- “Escalate now” first asks to confirm, naming the LLM proposal stage; the question closes by itself if the dispute moves on or settles meanwhile. An escalation sent just after the dispute moved on changes nothing, so no stage is skipped.
- **What the other person does** shows while the screen is open: a new offer (with the new split while the user edits their own, whose draft is kept), until the user opens the split editor or sends something, or the dispute leaves direct settlement; their acceptance of the user's proposal, until the screen is opened again; an escalation, by them or by itself, until the dispute moves on again. Nothing is shown for the user's own actions.
- Accepting accepts only the split the user saw: if the offer changed just before, nothing is accepted and the card asks to check the new one.
- **Errors.** Each part says when it failed, with “Try again”: a dispute that cannot be loaded fills the screen with Retry; a dispute that cannot be loaded again keeps the last version shown, with a note and Retry under the header; a proposal or an acceptance that fails shows its error in the proposal card; a failed proposal keeps the split being edited, so it can be sent again, and its error goes away if the user cancels the editor (Cancel is disabled while something is being sent); a failed escalation shows its error under “Escalate now”. An error goes away once the dispute moves on or settles, since its action no longer applies. Offline, the banner shows and every action that sends something is disabled.
- The rental chat is disabled while the dispute is open.
- For now, the evidence card has no photos, and the LLM proposal and court stages show “Coming soon”.

## Admin court ruling

This admin-only screen checks uploaded court rulings before they move any money. ViewModel: AdminCourtRulingViewModel, using DisputeRepository, HandoverRepository, RentalRepository, ListingRepository and ProfileRepository. Admins are identified by a custom claim on their Firebase account.

- A queue of uploaded rulings waiting for review, each with the item, both parties, the deposit, and the upload date.
- A detail view with the ruling PDF, the photos, the proposal history, and the LLM's earlier proposal.
- The split read from the PDF by Gemini, pre-filled in percent and editable by the admin.
- “Apply ruling” behind a confirmation step, since this cannot be undone. Applying it splits the deposit and closes the rental.
- “Reject” for an invalid or unreadable document; the uploader is notified and can upload again.
