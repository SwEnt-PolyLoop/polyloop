// Made with Claude.

import { DocumentData, Timestamp } from "firebase-admin/firestore";

/**
 * True if [uid] has seen the rental's last message: their `lastReadAt` entry is not older than
 * `lastMessageAt`, the same rule as the app's chat list, which shows them no red dot then.
 */
export function hasSeenLastMessage(rental: DocumentData | undefined, uid: string): boolean {
  const readAt = rental?.lastReadAt?.[uid] as Timestamp | undefined;
  const messageAt = rental?.lastMessageAt as Timestamp | undefined;
  return !!readAt && !!messageAt && readAt.toMillis() >= messageAt.toMillis();
}
