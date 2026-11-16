// Made with Claude.

package com.swent.polyloop.model.handover

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeHandoverRepositoryTest {

  private val repository = FakeHandoverRepository(currentUid = "u1")

  private fun photoIds(phase: HandoverPhase = HandoverPhase.PICKUP) = runBlocking {
    repository.observeMyPhotos("r1", phase).first().map { it.id }
  }

  @Test
  fun addedPhotosGetTheLowestFreeSlots() = runBlocking {
    repeat(3) { repository.addPhoto("r1", HandoverPhase.PICKUP, "file://$it") }

    assertEquals(listOf("u1_PICKUP_0", "u1_PICKUP_1", "u1_PICKUP_2"), photoIds())
  }

  @Test
  fun eachPhaseAndEachRentalCountsItsOwnSlots() = runBlocking {
    repository.addPhoto("r1", HandoverPhase.PICKUP, "file://a")
    repository.addPhoto("r1", HandoverPhase.RETURN, "file://b")
    repository.addPhoto("r2", HandoverPhase.PICKUP, "file://c")

    assertEquals(listOf("u1_PICKUP_0"), photoIds(HandoverPhase.PICKUP))
    assertEquals(listOf("u1_RETURN_0"), photoIds(HandoverPhase.RETURN))
    assertEquals(
        listOf("u1_PICKUP_0"),
        repository.observeMyPhotos("r2", HandoverPhase.PICKUP).first().map { it.id },
    )
  }

  @Test
  fun photosWithOtherIdsUseNoSlot() = runBlocking {
    repository.photos =
        listOf(
            FakeHandoverRepository.StoredPhoto(
                "r1",
                "u1",
                HandoverPhoto("random-id", HandoverPhase.PICKUP, repository.now, "file://old"),
            )
        )

    repository.addPhoto("r1", HandoverPhase.PICKUP, "file://new")

    assertEquals(listOf("random-id", "u1_PICKUP_0"), photoIds())
  }

  @Test
  fun aFreedSlotIsTakenAgain() = runBlocking {
    repeat(3) { repository.addPhoto("r1", HandoverPhase.PICKUP, "file://$it") }
    repository.photos = repository.photos.filter { it.photo.id != "u1_PICKUP_1" }

    repository.addPhoto("r1", HandoverPhase.PICKUP, "file://again")

    assertTrue("u1_PICKUP_1" in photoIds())
  }

  @Test
  fun withAllSlotsUsedAddPhotoFailsAndAddsNothing() = runBlocking {
    repeat(HandoverPhoto.MAX_PHOTOS) {
      repository.addPhoto("r1", HandoverPhase.PICKUP, "file://$it")
    }

    val result = repository.addPhoto("r1", HandoverPhase.PICKUP, "file://extra")

    assertTrue(result.exceptionOrNull() is IllegalStateException)
    assertEquals(HandoverPhoto.MAX_PHOTOS, photoIds().size)
  }
}
