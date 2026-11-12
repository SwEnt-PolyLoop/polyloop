// Made with Claude.

import {
  DocumentData,
  DocumentReference,
  DocumentSnapshot,
  FieldValue,
  Firestore,
  QueryDocumentSnapshot,
  Timestamp,
  Transaction,
} from "firebase-admin/firestore";
import {
  DateRange,
  OfferAnswer,
  OfferInput,
  RequestError,
  conflicts,
  isValidDeposit,
  listingTitleOf,
  offerExpiry,
  readRange,
  requirePerson,
} from "./rentalRules";
import { runSweep } from "./sweep";
import {
  Wallet,
  hasTransaction,
  readWallet,
  rentalHeldRowId,
  rentalReleasedRowId,
  requireFunds,
  writeDepositHold,
  writeRentalRelease,
} from "./wallet";

/** Firestore names, as in "Firestore schema" in docs/ARCHITECTURE.md. */
const RENTALS = "rentals";
const LISTINGS = "listings";
const REQUESTED = "REQUESTED";
const DECLINED = "DECLINED";

/** The offer stored on a rental, once read back. */
interface StoredOffer {
  amount: number;
  authorId: string;
  isCounter: boolean;
  expiresAt: Timestamp;
}

/** What a sweep did: the offers it expired, and those it could not, which the next sweep retries. */
export interface SweepResult {
  expired: number;
  failed: number;
}

/**
 * Sends the lender's first offer on the request [input.rentalId], in one transaction. The amount
 * goes on the rental, and the exact addresses in `private/addresses`, which only Cloud Functions
 * read, so the borrower sees them only once the rental is accepted.
 *
 * @param db the Firestore database.
 * @param lenderId the caller, who must be the rental's lender.
 * @param input the rental, the deposit and both addresses, already checked by `parseOfferInput`.
 * @param now the current time, passed so that tests can set it.
 * @throws RequestError `not-found`, `permission-denied` (not the lender) or `failed-precondition`
 *   (not requested, an offer already exists, the rental's first day has come, or the dates are no
 *   longer free). Nothing is written then.
 */
export async function makeOffer(
  db: Firestore,
  lenderId: string,
  input: OfferInput,
  now: Date = new Date(),
): Promise<void> {
  const rentalRef = db.collection(RENTALS).doc(input.rentalId);
  await db.runTransaction(async (tx) => {
    const rental = requireRequest(await tx.get(rentalRef));
    requireLender(rental, lenderId);
    if (rental.offer) {
      throw new RequestError("failed-precondition", "An offer was already sent for this request.");
    }
    const dates = requireDates(rental);
    const expiresAt = offerExpiry(now, dates.start);
    if (expiresAt.getTime() <= now.getTime()) {
      throw new RequestError("failed-precondition", "This rental's first day has already come.");
    }
    await requireFreeListing(tx, rentalRef, rental, dates);

    tx.set(addressesRef(rentalRef), { pickup: input.pickupAddress, dropOff: input.returnAddress });
    writeWithSystemMessage(tx, rentalRef, `Offer sent · ${input.deposit} PP deposit`, {
      offer: {
        amount: input.deposit,
        authorId: lenderId,
        isCounter: false,
        createdAt: FieldValue.serverTimestamp(),
        expiresAt: Timestamp.fromDate(expiresAt),
      },
    });
  });
}

/**
 * Answers the offer waiting on a rental, in one transaction. Only the participant who did not write
 * the offer can answer it, and only the lender's first offer can be countered.
 *
 * Accepting makes the rental ACCEPTED with the offered deposit and the exact addresses, adds its
 * dates to the listing's `bookedRanges`, and declines every other REQUESTED rental of the listing
 * whose dates overlap (buffer day included). It also holds the deposit, the offered amount (the
 * counter amount after a counteroffer), in the borrower's wallet, whoever accepts. The rental price
 * of each request it declines is given back to its borrower (see `releaseHolds`); the price of the
 * accepted rental stays reserved. Declining the offer gives the price back too.
 *
 * @param db the Firestore database.
 * @param callerId the participant answering.
 * @param answer the answer, already checked by `parseOfferAnswer`.
 * @param now the current time, passed so that tests can set it.
 * @throws RequestError `not-found`, `permission-denied` (not a participant, or the offer's author),
 *   `failed-precondition` (no offer waiting, expired, already countered, dates no longer free, or,
 *   on ACCEPT or COUNTER, the borrower's available balance is below the deposit) or `internal` (the
 *   stored rental or offer is broken: no valid dates, borrower, addresses or deposit). Nothing is
 *   written then.
 */
export async function respondToOffer(
  db: Firestore,
  callerId: string,
  answer: OfferAnswer,
  now: Date = new Date(),
): Promise<void> {
  const rentalRef = db.collection(RENTALS).doc(answer.rentalId);
  await db.runTransaction(async (tx) => {
    const rental = await requireAnswerableRental(tx, rentalRef, callerId);
    const offer = requireLiveOffer(rental, callerId, now);

    switch (answer.action) {
      case "ACCEPT":
        await accept(tx, rentalRef, rental, offer);
        return;
      case "DECLINE":
        await declineAndRelease(tx, rentalRef, rental, "Offer declined");
        return;
      case "COUNTER":
        if (offer.isCounter) {
          throw new RequestError("failed-precondition", "A counteroffer cannot be countered.");
        }
        // Only the borrower counters, so this is the caller's own wallet.
        requireFunds(await readWallet(tx, rentalRef.firestore, callerId), answer.deposit);
        writeWithSystemMessage(tx, rentalRef, `Counteroffer · ${answer.deposit} PP`, {
          offer: {
            amount: answer.deposit,
            previousAmount: offer.amount,
            authorId: callerId,
            isCounter: true,
            createdAt: FieldValue.serverTimestamp(),
            expiresAt: Timestamp.fromDate(offerExpiry(now, requireDates(rental).start)),
          },
        });
    }
  });
}

/**
 * Declines a request that has no offer yet, in one transaction. Once an offer exists, the lender
 * waits for the borrower's answer.
 *
 * @param db the Firestore database.
 * @param lenderId the caller, who must be the rental's lender.
 * @param rentalId the request to decline.
 * @throws RequestError `not-found`, `permission-denied` (not the lender) or `failed-precondition`
 *   (not requested, or an offer exists). Nothing is written then.
 */
export async function declineRequest(
  db: Firestore,
  lenderId: string,
  rentalId: string,
): Promise<void> {
  const rentalRef = db.collection(RENTALS).doc(rentalId);
  await db.runTransaction(async (tx) => {
    const rental = requireRequest(await tx.get(rentalRef));
    requireLender(rental, lenderId);
    if (rental.offer) {
      throw new RequestError("failed-precondition", "An offer is waiting for an answer.");
    }
    await declineAndRelease(tx, rentalRef, rental, "Request declined");
  });
}

/**
 * Declines every rental whose offer has expired at [now]. One failure is logged and does not stop
 * the others. A finished negotiation has no `offer` left, so the query, on one field and needing no
 * composite index, only finds offers still waiting.
 *
 * @param db the Firestore database.
 * @param now the current time, passed so that tests can set it.
 * @param expire handles one rental; tests pass one that fails.
 * @returns how many offers were expired and how many could not be.
 */
export async function expireOverdueOffers(
  db: Firestore,
  now: Date,
  expire: typeof expireOffer = expireOffer,
): Promise<SweepResult> {
  const overdue = await db
    .collection(RENTALS)
    .where("offer.expiresAt", "<=", Timestamp.fromDate(now))
    .select()
    .get();
  const { done, failed } = await runSweep(
    overdue.docs.map((doc) => doc.ref),
    (ref) => expire(ref, now),
    (id) => `Could not expire the offer of rental ${id}`,
  );
  return { expired: done, failed };
}

/**
 * Declines one rental if its offer has still expired at [now], in one transaction. The check is
 * made again because the offer may have been answered or countered since the query.
 *
 * @param rentalRef the rental to check.
 * @param now the current time.
 * @returns true if the rental was declined, false if there was nothing to do.
 */
export async function expireOffer(rentalRef: DocumentReference, now: Date): Promise<boolean> {
  return rentalRef.firestore.runTransaction(async (tx) => {
    const rental = (await tx.get(rentalRef)).data();
    const offer = rental?.offer as StoredOffer | undefined;
    if (rental?.status !== REQUESTED || !offer || !isExpired(offer, now)) {
      return false;
    }
    await declineAndRelease(tx, rentalRef, rental, "Offer expired · request declined");
    return true;
  });
}

/**
 * The acceptance: reads the listing, the addresses, the other requests and the borrower's wallet,
 * then writes it all, holding the deposit last.
 */
async function accept(
  tx: Transaction,
  rentalRef: DocumentReference,
  rental: DocumentData,
  offer: StoredOffer,
): Promise<void> {
  const dates = requireDates(rental);
  const db = rentalRef.firestore;

  const listingRef = await requireFreeListing(tx, rentalRef, rental, dates);

  const addresses = (await tx.get(addressesRef(rentalRef))).data();
  if (!addresses) {
    throw new RequestError("internal", "The addresses of this offer are missing.");
  }

  // Equality filters only, so no composite index is needed; the date check is done here.
  const pending = await tx.get(
    db
      .collection(RENTALS)
      .where("listingId", "==", rental.listingId)
      .where("status", "==", REQUESTED),
  );
  const overlapping = pending.docs.filter(
    (doc) => doc.id !== rentalRef.id && overlapsRequest(doc, dates),
  );

  // The borrower pays, whoever accepts. The amount is the offer's: `deposit` is set only below.
  const borrowerId = requirePerson(rental.borrowerId, "borrower");
  if (!isValidDeposit(offer.amount)) {
    throw new RequestError("internal", "The offer has no valid deposit.");
  }
  const wallet = await readWallet(tx, db, borrowerId);
  requireFunds(wallet, offer.amount);
  // The funds are checked on the balance as it is now: the prices released below do not count.
  const wallets: Wallets = new Map([[borrowerId, wallet]]);
  const holds = await readHolds(
    tx,
    db,
    overlapping.map((doc) => ({ ref: doc.ref, rental: doc.data() })),
    wallets,
  );

  tx.delete(addressesRef(rentalRef));
  writeWithSystemMessage(tx, rentalRef, `Accepted · ${offer.amount} PP deposit`, {
    status: "ACCEPTED",
    deposit: offer.amount,
    pickupAddress: addresses.pickup,
    returnAddress: addresses.dropOff,
    offer: FieldValue.delete(),
  });
  tx.update(listingRef, {
    bookedRanges: FieldValue.arrayUnion({ start: dates.start, end: dates.end }),
  });
  for (const doc of overlapping) {
    decline(tx, doc.ref, "Dates booked · request declined");
  }
  // The releases come first: a borrower with a competing request is also the one who pays the
  // deposit, from a wallet that has just got that request's price back.
  releaseHolds(tx, db, holds, wallets);
  // No hasTransaction read: a REQUESTED rental cannot have its deposit row yet.
  writeDepositHold(
    tx,
    db,
    borrowerId,
    wallets.get(borrowerId) ?? wallet,
    offer.amount,
    rentalRef.id,
    listingTitleOf(rental),
  );
}

/** The wallets read in a transaction, by user id, as they are after the writes made so far. */
type Wallets = Map<string, Wallet>;

/** The reserved price of a request that is ending, found by [readHolds]. */
interface Hold {
  rentalId: string;
  borrowerId: string;
  price: number;
  itemTitle: string;
}

/**
 * Declines [rental] and gives its price back to the borrower, in the caller's transaction. Reads
 * first, then writes, so it must be called before the caller's first write.
 */
async function declineAndRelease(
  tx: Transaction,
  rentalRef: DocumentReference,
  rental: DocumentData,
  text: string,
): Promise<void> {
  const wallets: Wallets = new Map();
  const holds = await readHolds(tx, rentalRef.firestore, [{ ref: rentalRef, rental }], wallets);
  decline(tx, rentalRef, text);
  releaseHolds(tx, rentalRef.firestore, holds, wallets);
}

/**
 * Finds which of the requests [ended] have a price to give back: the `rental_held_<id>` row exists
 * and the `rental_released_<id>` row does not. A request sent before the price was reserved has no
 * such row and gets nothing back, so nothing is ever credited that was not held. Also reads the
 * wallet of each of those borrowers into [wallets], unless it is already there. Reads only.
 */
async function readHolds(
  tx: Transaction,
  db: Firestore,
  ended: { ref: DocumentReference; rental: DocumentData }[],
  wallets: Wallets,
): Promise<Hold[]> {
  const holds: Hold[] = [];
  for (const { ref, rental } of ended) {
    const { borrowerId, totalPrice } = rental;
    if (typeof borrowerId !== "string" || borrowerId === "") continue;
    if (typeof totalPrice !== "number" || !Number.isInteger(totalPrice) || totalPrice < 1) continue;
    if (!(await hasTransaction(tx, db, borrowerId, rentalHeldRowId(ref.id)))) continue;
    if (await hasTransaction(tx, db, borrowerId, rentalReleasedRowId(ref.id))) continue;
    holds.push({
      rentalId: ref.id,
      borrowerId,
      price: totalPrice,
      itemTitle: listingTitleOf(rental),
    });
  }
  for (const { borrowerId } of holds) {
    if (!wallets.has(borrowerId)) wallets.set(borrowerId, await readWallet(tx, db, borrowerId));
  }
  return holds;
}

/**
 * Gives back the price of each of [holds], one after the other: when a borrower has several, each
 * release starts from the wallet the previous one left, kept in [wallets]. Runs inside the caller's
 * transaction, after all its reads.
 */
function releaseHolds(tx: Transaction, db: Firestore, holds: Hold[], wallets: Wallets): void {
  for (const hold of holds) {
    const wallet = wallets.get(hold.borrowerId);
    if (!wallet) continue;
    const released = writeRentalRelease(
      tx,
      db,
      hold.borrowerId,
      wallet,
      hold.price,
      hold.rentalId,
      hold.itemTitle,
    );
    wallets.set(hold.borrowerId, {
      ...wallet,
      available: wallet.available + released,
      heldForRentals: wallet.heldForRentals - released,
    });
  }
}

/**
 * Ends a negotiation with no rental: DECLINED, with no offer and no addresses left, and [text] in
 * the chat. Runs inside the caller's transaction, after its reads.
 */
function decline(tx: Transaction, rentalRef: DocumentReference, text: string): void {
  tx.delete(addressesRef(rentalRef));
  writeWithSystemMessage(tx, rentalRef, text, { status: DECLINED, offer: FieldValue.delete() });
}

/** Updates the rental with [fields], and adds [text] to its chat as a SYSTEM message. */
export function writeWithSystemMessage(
  tx: Transaction,
  rentalRef: DocumentReference,
  text: string,
  fields: DocumentData,
): void {
  tx.update(rentalRef, {
    ...fields,
    lastMessage: text,
    lastMessageAt: FieldValue.serverTimestamp(),
  });
  tx.set(rentalRef.collection("messages").doc(), {
    senderId: "",
    text,
    sentAt: FieldValue.serverTimestamp(),
    type: "SYSTEM",
  });
}

function addressesRef(rentalRef: DocumentReference): DocumentReference {
  return rentalRef.collection("private").doc("addresses");
}

/** The data of a REQUESTED rental, or a refusal. */
function requireRequest(snapshot: DocumentSnapshot): DocumentData {
  const rental = snapshot.data();
  if (!rental) {
    throw new RequestError("not-found", "This rental does not exist.");
  }
  if (rental.status !== REQUESTED) {
    throw new RequestError("failed-precondition", "This request was already answered.");
  }
  return rental;
}

/** Same as [requireRequest], but a caller who takes no part in the rental is refused first. */
async function requireAnswerableRental(
  tx: Transaction,
  rentalRef: DocumentReference,
  callerId: string,
): Promise<DocumentData> {
  const snapshot = await tx.get(rentalRef);
  const isParticipant =
    callerId === snapshot.get("ownerId") || callerId === snapshot.get("borrowerId");
  if (snapshot.exists && !isParticipant) {
    throw new RequestError("permission-denied", "Only the two participants can answer an offer.");
  }
  return requireRequest(snapshot);
}

function requireLender(rental: DocumentData, callerId: string): void {
  if (rental.ownerId !== callerId) {
    throw new RequestError("permission-denied", "Only the lender can do this.");
  }
}

/** The rental's dates, or a refusal if they are invalid. */
function requireDates(rental: DocumentData): DateRange {
  const dates = readRange({ start: rental.startDate, end: rental.endDate });
  if (!dates) {
    throw new RequestError("internal", "The rental has no valid dates.");
  }
  return dates;
}

/** The listing of [rental], or a refusal if it is gone or [dates] conflict with a booking. */
async function requireFreeListing(
  tx: Transaction,
  rentalRef: DocumentReference,
  rental: DocumentData,
  dates: DateRange,
): Promise<DocumentReference> {
  const listingRef = rentalRef.firestore.collection(LISTINGS).doc(rental.listingId);
  const listing = await tx.get(listingRef);
  if (!listing.exists) {
    throw new RequestError("not-found", "The listing of this rental does not exist.");
  }
  if (bookedRanges(listing.get("bookedRanges")).some((range) => conflicts(range, dates))) {
    throw new RequestError("failed-precondition", "These dates are no longer free.");
  }
  return listingRef;
}

/** True once [offer] can no longer be accepted, and `expireOffers` may decline its rental. */
function isExpired(offer: StoredOffer, now: Date): boolean {
  return offer.expiresAt.toMillis() <= now.getTime();
}

/** The offer that [callerId] may answer at [now], or a refusal. */
function requireLiveOffer(rental: DocumentData, callerId: string, now: Date): StoredOffer {
  const offer = rental.offer as StoredOffer | undefined;
  if (!offer) {
    throw new RequestError("failed-precondition", "No offer is waiting for an answer.");
  }
  if (offer.authorId === callerId) {
    throw new RequestError("permission-denied", "You cannot answer your own offer.");
  }
  if (isExpired(offer, now)) {
    throw new RequestError("failed-precondition", "This offer has expired.");
  }
  return offer;
}

/** The valid ranges of a listing's `bookedRanges`; malformed entries are ignored. */
function bookedRanges(value: unknown): DateRange[] {
  if (!Array.isArray(value)) return [];
  return value.map(readRange).filter((range): range is DateRange => range !== null);
}

/** True if a stored request's dates conflict with [dates], buffer days included. */
function overlapsRequest(doc: QueryDocumentSnapshot, dates: DateRange): boolean {
  const range = readRange({ start: doc.get("startDate"), end: doc.get("endDate") });
  return range !== null && conflicts(range, dates);
}
