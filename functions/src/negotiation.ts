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
  offerExpiry,
  readRange,
} from "./rentalRules";

/** Firestore names, as in "Firestore schema" in docs/ARCHITECTURE.md. */
const RENTALS = "rentals";
const LISTINGS = "listings";
const REQUESTED = "REQUESTED";
const DECLINED = "DECLINED";

/** What an answer did: the other requests an acceptance declined because their dates overlapped. */
export interface AnswerResult {
  declinedRentalIds: string[];
}

/** The offer stored on a rental, once read back. */
interface StoredOffer {
  amount: number;
  authorId: string;
  isCounter: boolean;
  expiresAt: Timestamp;
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
 *   (not requested, an offer already exists, or the rental's first day has come). Nothing is
 *   written then.
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
    const expiresAt = offerExpiry(now, requireDates(rental).start);
    if (expiresAt.getTime() <= now.getTime()) {
      throw new RequestError("failed-precondition", "This rental's first day has already come.");
    }

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
 * whose dates overlap (buffer day included). Reserving the deposit waits for the wallet's Firestore
 * implementation, so the balance is not checked yet.
 *
 * @param db the Firestore database.
 * @param callerId the participant answering.
 * @param answer the answer, already checked by `parseOfferAnswer`.
 * @param now the current time, passed so that tests can set it.
 * @returns the requests declined by an acceptance; empty for any other answer.
 * @throws RequestError `not-found`, `permission-denied` (not a participant, or the offer's author)
 *   or `failed-precondition` (no offer waiting, expired, already countered, or dates no longer
 *   free). Nothing is written then.
 */
export async function respondToOffer(
  db: Firestore,
  callerId: string,
  answer: OfferAnswer,
  now: Date = new Date(),
): Promise<AnswerResult> {
  const rentalRef = db.collection(RENTALS).doc(answer.rentalId);
  return db.runTransaction(async (tx) => {
    const rental = await requireAnswerableRental(tx, rentalRef, callerId);
    const offer = requireLiveOffer(rental, callerId, now);

    switch (answer.action) {
      case "ACCEPT":
        return accept(tx, rentalRef, rental, offer);
      case "DECLINE":
        decline(tx, rentalRef, "Offer declined");
        return { declinedRentalIds: [] };
      case "COUNTER":
        if (offer.isCounter) {
          throw new RequestError("failed-precondition", "A counteroffer cannot be countered.");
        }
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
        return { declinedRentalIds: [] };
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
    decline(tx, rentalRef, "Request declined");
  });
}

/** The acceptance: reads the listing, the addresses and the other requests, then writes it all. */
async function accept(
  tx: Transaction,
  rentalRef: DocumentReference,
  rental: DocumentData,
  offer: StoredOffer,
): Promise<AnswerResult> {
  const dates = requireDates(rental);
  const db = rentalRef.firestore;

  const listingRef = db.collection(LISTINGS).doc(rental.listingId);
  const listingSnap = await tx.get(listingRef);
  if (!listingSnap.exists) {
    throw new RequestError("not-found", "The listing of this rental does not exist.");
  }
  if (bookedRanges(listingSnap.get("bookedRanges")).some((range) => conflicts(range, dates))) {
    throw new RequestError("failed-precondition", "These dates are no longer free.");
  }

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
  return { declinedRentalIds: overlapping.map((doc) => doc.id) };
}

/**
 * Ends a negotiation with no rental: DECLINED, with no offer and no addresses left, and [text] in
 * the chat. Runs inside the caller's transaction, after its reads.
 */
export function decline(tx: Transaction, rentalRef: DocumentReference, text: string): void {
  tx.delete(addressesRef(rentalRef));
  writeWithSystemMessage(tx, rentalRef, text, { status: DECLINED, offer: FieldValue.delete() });
}

/** Updates the rental with [fields], and adds [text] to its chat as a SYSTEM message. */
function writeWithSystemMessage(
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

/** The offer that [callerId] may answer at [now], or a refusal. */
function requireLiveOffer(rental: DocumentData, callerId: string, now: Date): StoredOffer {
  const offer = rental.offer as StoredOffer | undefined;
  if (!offer) {
    throw new RequestError("failed-precondition", "No offer is waiting for an answer.");
  }
  if (offer.authorId === callerId) {
    throw new RequestError("permission-denied", "You cannot answer your own offer.");
  }
  if (offer.expiresAt.toMillis() <= now.getTime()) {
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
