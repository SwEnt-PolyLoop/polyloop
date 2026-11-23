// Made with Claude.

package com.swent.polyloop.model.handover

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

  private val phase = HandoverPhase.PICKUP

  @Test
  fun theCodeIsShownAsItIs() = runTest {
    assertEquals("secret-code", FakeHandoverRepository().getCodeToShow("r1", phase).getOrThrow())
  }

  @Test
  fun theFingerprintMatchesTheCodeShown() = runTest {
    val fingerprint = FakeHandoverRepository().getCodeFingerprint("r1", phase).getOrThrow()!!
    assertTrue(HandoverCode.matches("secret-code", fingerprint))
  }

  @Test
  fun withoutACodeBothReadsGiveNull() = runTest {
    val repository = FakeHandoverRepository(code = null)
    assertNull(repository.getCodeToShow("r1", phase).getOrThrow())
    assertNull(repository.getCodeFingerprint("r1", phase).getOrThrow())
  }

  @Test
  fun readsFailWithTheCodeFailure() = runTest {
    val error = IOException("offline")
    val repository = FakeHandoverRepository(codeFailure = error)
    assertEquals(error, repository.getCodeToShow("r1", phase).exceptionOrNull())
    assertEquals(error, repository.getCodeFingerprint("r1", phase).exceptionOrNull())
  }

  @Test
  fun readsFailWhenNobodyIsSignedIn() = runTest {
    val repository = FakeHandoverRepository(currentUid = null)
    assertTrue(repository.getCodeToShow("r1", phase).exceptionOrNull() is IllegalStateException)
    assertTrue(
        repository.getCodeFingerprint("r1", phase).exceptionOrNull() is IllegalStateException
    )
  }

  @Test
  fun theRightCodeIsConfirmedAndRecorded() = runTest {
    val repository = FakeHandoverRepository()
    assertTrue(repository.confirmScan("r1", phase, "secret-code").isSuccess)
    assertEquals(listOf("secret-code"), repository.scannedCodes)
  }

  @Test
  fun aWrongCodeFailsButIsRecorded() = runTest {
    val repository = FakeHandoverRepository()
    val result = repository.confirmScan("r1", phase, "other")
    assertTrue(result.exceptionOrNull() is WrongCodeException)
    assertEquals(listOf("other"), repository.scannedCodes)
  }

  @Test
  fun anyCodeIsWrongWhenThereIsNone() = runTest {
    val result = FakeHandoverRepository(code = null).confirmScan("r1", phase, "secret-code")
    assertTrue(result.exceptionOrNull() is WrongCodeException)
  }

  @Test
  fun theRightCodeFailsWithTheScanFailure() = runTest {
    val repository = FakeHandoverRepository(scanFailure = RentalChangedException())
    val result = repository.confirmScan("r1", phase, "secret-code")
    assertTrue(result.exceptionOrNull() is RentalChangedException)
  }

  @Test
  fun aWrongCodeIsReportedBeforeTheScanFailure() = runTest {
    val repository = FakeHandoverRepository(scanFailure = IOException("offline"))
    val result = repository.confirmScan("r1", phase, "other")
    assertTrue(result.exceptionOrNull() is WrongCodeException)
  }

  @Test
  fun scanningFailsWhenNobodyIsSignedIn() = runTest {
    val result = FakeHandoverRepository(currentUid = null).confirmScan("r1", phase, "secret-code")
    assertTrue(result.exceptionOrNull() is IllegalStateException)
  }

  @Test
  fun theScanWaitsForTheGate() = runTest {
    val gate = CompletableDeferred<Unit>()
    val repository = FakeHandoverRepository(gate = gate)
    var done = false
    launch {
      repository.confirmScan("r1", phase, "secret-code")
      done = true
    }
    runCurrent()
    assertFalse(done)
    assertEquals(1, repository.scannedCodes.size) // recorded as soon as it is sent

    gate.complete(Unit)
    runCurrent()
    assertTrue(done)
  }

  // ---------- photos added at once, on different threads ----------

  /** [count] calls of addPhoto at once on real threads: runTest would run them one at a time. */
  private fun addAtOnce(repository: FakeHandoverRepository, count: Int): List<Result<Unit>> =
      runBlocking {
        coroutineScope {
          List(count) {
                async(Dispatchers.Default) {
                  repository.addPhoto("r1", HandoverPhase.PICKUP, "file://$it")
                }
              }
              .awaitAll()
        }
      }

  private fun slots(count: Int) = List(count) { "u1_PICKUP_$it" }

  @Test
  fun twoPhotosAddedAtOnceGetDifferentSlots() {
    var badRounds = 0
    repeat(200) {
      val repository = FakeHandoverRepository()

      val results = addAtOnce(repository, 2)

      val ids = repository.photos.map { it.photo.id }
      if (!results.all { it.isSuccess } || ids.sorted() != slots(2)) badRounds++
    }

    assertEquals("rounds out of 200 where two photos got the same slot", 0, badRounds)
  }

  @Test
  fun theMaximumNumberOfPhotosAddedAtOnceGetOneSlotEach() {
    var badRounds = 0
    repeat(100) {
      val repository = FakeHandoverRepository()

      val results = addAtOnce(repository, HandoverPhoto.MAX_PHOTOS)

      val ids = repository.photos.map { it.photo.id }
      if (
          !results.all { it.isSuccess } || ids.sorted() != slots(HandoverPhoto.MAX_PHOTOS).sorted()
      ) {
        badRounds++
      }
    }

    assertEquals("rounds out of 100 where the photos did not get one slot each", 0, badRounds)
  }

  @Test
  fun morePhotosThanTheMaximumAddedAtOnceFailTheExtraOnes() {
    var badRounds = 0
    repeat(100) {
      val repository = FakeHandoverRepository()

      val results = addAtOnce(repository, HandoverPhoto.MAX_PHOTOS + 5)

      val ids = repository.photos.map { it.photo.id }
      val refused = results.filter { it.isFailure }
      if (
          results.count { it.isSuccess } != HandoverPhoto.MAX_PHOTOS ||
              refused.size != 5 ||
              refused.any { it.exceptionOrNull() !is IllegalStateException } ||
              ids.sorted() != slots(HandoverPhoto.MAX_PHOTOS).sorted()
      ) {
        badRounds++
      }
    }

    assertEquals("rounds out of 100 that did not keep the cap exactly", 0, badRounds)
  }

  @Test
  fun confirmingAReturnRecordsItsRental() = runTest {
    val repository = FakeHandoverRepository()
    assertTrue(repository.confirmReturnOk("r1").isSuccess)
    assertEquals(listOf("r1"), repository.confirmedReturns)
  }

  @Test
  fun confirmingAReturnFailsWithTheConfirmFailureOrWhenNobodyIsSignedIn() = runTest {
    val refused = FakeHandoverRepository(confirmFailure = IOException("offline"))
    assertTrue(refused.confirmReturnOk("r1").exceptionOrNull() is IOException)
    val signedOut = FakeHandoverRepository(currentUid = null)
    assertTrue(signedOut.confirmReturnOk("r1").exceptionOrNull() is IllegalStateException)
    assertEquals(emptyList<String>(), refused.confirmedReturns + signedOut.confirmedReturns)
  }
}
