// Made with Claude.

package com.swent.polyloop.model.handover

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.storage.StorageException
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Which errors make [HandoverPhotoWorker] give a photo up, and which keep it for a retry. */
@RunWith(RobolectricTestRunner::class)
class HandoverPhotoRefusalTest {

  private fun firestore(code: Code) = FirebaseFirestoreException(code.name, code)

  // Storage reads its error code from the HTTP status of the answer (0: none, the request failed).
  private fun storage(httpCode: Int): StorageException =
      requireNotNull(StorageException.fromExceptionAndHttpCode(IOException("failed"), httpCode))

  @Test
  fun errorsTheServerWouldGiveAgainAreRefusals() {
    for (code in listOf(Code.PERMISSION_DENIED, Code.INVALID_ARGUMENT, Code.UNAUTHENTICATED)) {
      assertTrue(code.name, HandoverPhotoWorker.isRefusal(firestore(code)))
    }
    assertTrue(HandoverPhotoWorker.isRefusal(storage(403))) // not authorized
    assertTrue(HandoverPhotoWorker.isRefusal(storage(401))) // not authenticated
  }

  @Test
  fun errorsThatMayPassAreRetried() {
    for (code in listOf(Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.UNKNOWN)) {
      assertFalse(code.name, HandoverPhotoWorker.isRefusal(firestore(code)))
    }
    assertFalse(HandoverPhotoWorker.isRefusal(storage(0)))
    assertFalse(HandoverPhotoWorker.isRefusal(IOException("connection reset")))
  }
}
