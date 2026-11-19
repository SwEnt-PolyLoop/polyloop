// Made with Claude.

package com.swent.polyloop.model.handover

/**
 * A condition photo's id is a fixed slot, `{uid}_{phase}_{n}` with n from 0 to
 * [HandoverPhoto.MAX_PHOTOS] - 1, so that the Security Rules can cap the photos of one person and
 * phase without counting documents: photos are never replaced, so each slot is written once.
 */
internal fun photoSlotId(uid: String, phase: HandoverPhase, slot: Int): String =
    "${uid}_${phase.name}_$slot"

/**
 * The lowest slot of [uid] and [phase] that none of [existingIds] uses, or null when all
 * [HandoverPhoto.MAX_PHOTOS] are used. An id that is not a slot of [uid] and [phase], such as the
 * random id of a photo taken before slots existed, uses none.
 */
internal fun lowestFreeSlot(
    uid: String,
    phase: HandoverPhase,
    existingIds: Collection<String>,
): Int? {
  val used = existingIds.toSet()
  return (0 until HandoverPhoto.MAX_PHOTOS).firstOrNull { photoSlotId(uid, phase, it) !in used }
}
