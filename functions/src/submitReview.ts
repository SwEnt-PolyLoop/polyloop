// Made with Claude.

import { DocumentData, FieldValue, Firestore, getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { toCallableError } from "./callable";
import {
  RequestError,
  asFields,
  listingTitleOf,
  parseRentalId,
  requireParties,
  requireVerifiedEpfl,
} from "./rentalRules";

/** The reason the app reads (`details.reason`) when the rental is not closed yet. */
export const NOT_CLOSED = "NOT_CLOSED";

/** What the app sends: the rental and the stars given to the other participant. */
export interface ReviewInput {
  rentalId: string;
  stars: number;
}

/**
 * The input of a review: whole [ReviewInput.stars] from 1 to 5 and a rental id. Throws an
 * `invalid-argument` error naming the first field that is wrong.
 */
export function parseReviewInput(data: unknown): ReviewInput {
  const stars = asFields(data).stars;
  if (typeof stars !== "number" || !Number.isInteger(stars) || stars < 1 || stars > 5) {
    throw new RequestError("invalid-argument", "stars must be a whole number from 1 to 5.");
  }
  return { rentalId: parseRentalId(data), stars };
}

/**
 * Saves the review of [authorId] on the closed rental [rentalId] and updates the other
 * participant's rating, in one transaction: `reviews/{rentalId}_{authorId}` is created with the
 * author's name and the item's title copied from the rental, and the reviewee's `reviewCount` goes
 * up by 1 while their `averageRating` takes in [stars] (a missing field reads as 0; not rounded).
 * The checks run in this order: the rental exists, the author is a participant, the rental has a
 * valid borrower and lender, the rental is CLOSED, the author has not reviewed it yet, the reviewee
 * has a profile, and the reviewee's stored rating is valid.
 *
 * @throws RequestError `not-found` (no rental, or no profile for the reviewee),
 *   `permission-denied` (not a participant), `failed-precondition` with the reason [NOT_CLOSED],
 *   `already-exists` (a second review by the same person) or `internal` (the rental has no valid
 *   borrower or lender, or the reviewee's stored rating is not valid, see [newRating]). Nothing is
 *   written then.
 */
export async function submitReviewInTransaction(
  db: Firestore,
  authorId: string,
  { rentalId, stars }: ReviewInput,
): Promise<void> {
  const rentalRef = db.collection("rentals").doc(rentalId);
  const reviewRef = db.collection("reviews").doc(`${rentalId}_${authorId}`);
  await db.runTransaction(async (tx) => {
    const rental = (await tx.get(rentalRef)).data();
    if (!rental) {
      throw new RequestError("not-found", "This rental does not exist.");
    }
    if (authorId !== rental.ownerId && authorId !== rental.borrowerId) {
      throw new RequestError("permission-denied", "Only the two participants can do this.");
    }
    const { borrowerId, lenderId } = requireParties(rental);
    if (rental.status !== "CLOSED") {
      throw new RequestError("failed-precondition", "Only a closed rental can be reviewed.", {
        reason: NOT_CLOSED,
      });
    }
    const authorIsLender = authorId === lenderId;
    const revieweeId = authorIsLender ? borrowerId : lenderId;
    const revieweeRef = db.collection("users").doc(revieweeId);
    const [review, reviewee] = await Promise.all([tx.get(reviewRef), tx.get(revieweeRef)]);
    if (review.exists) {
      throw new RequestError("already-exists", "You have already reviewed this rental.");
    }
    if (!reviewee.exists) {
      throw new RequestError("not-found", "The other person has no profile.");
    }

    tx.create(reviewRef, {
      rentalId,
      authorId,
      authorName: textOf(authorIsLender ? rental.ownerName : rental.borrowerName),
      revieweeId,
      stars,
      itemTitle: listingTitleOf(rental),
      createdAt: FieldValue.serverTimestamp(),
    });
    tx.update(revieweeRef, newRating(reviewee.data() as DocumentData, stars));
  });
}

/**
 * The reviewee's rating once [stars] is added: one more review, and the average over all of them,
 * worked out from the stored average and count. A missing or invalid count reads as 0, and the
 * average then counts as 0 too.
 *
 * @throws RequestError `internal` when the count is above 0 but the stored average is not a number
 *   from 1 to 5: the profile's rating is broken then, and adding to it would spread the error.
 */
export function newRating(
  profile: DocumentData,
  stars: number,
): { averageRating: number; reviewCount: number } {
  const count = countOrZero(profile.reviewCount);
  const average = count === 0 ? 0 : requireAverage(profile.averageRating);
  return {
    averageRating: (average * count + stars) / (count + 1),
    reviewCount: count + 1,
  };
}

/** [value] if it is a whole number from 0 up, otherwise 0. */
function countOrZero(value: unknown): number {
  return typeof value === "number" && Number.isInteger(value) && value > 0 ? value : 0;
}

/** [value] if it is a finite number from 1 to 5, or an `internal` refusal. */
function requireAverage(value: unknown): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 1 || value > 5) {
    throw new RequestError("internal", "The other person's rating is not valid.");
  }
  return value;
}

/** [value] if it is a string, otherwise "". */
function textOf(value: unknown): string {
  return typeof value === "string" ? value : "";
}

/**
 * Callable `submitReview` (see docs/ARCHITECTURE.md): a participant of a closed rental gives the
 * other one 1 to 5 stars, once. Input: `{rentalId, stars}`; everything else comes from the rental.
 * A rental that is not closed is refused with `details.reason` `NOT_CLOSED`. Returns {}.
 */
export const submitReview = onCall(async (request) => {
  try {
    const uid = requireVerifiedEpfl(request.auth);
    const input = parseReviewInput(request.data);
    await submitReviewInTransaction(getFirestore(), uid, input);
    return {};
  } catch (error) {
    throw toCallableError(error);
  }
});
