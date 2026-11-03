// Made with Claude.

package com.swent.polyloop.model.rental

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The answer calls of [RentalRepositoryFirestore]: they call the right Cloud Function with the
 * answer as data, and report a refusal from the server as a failure. The Functions themselves are
 * tested in functions/src (on the emulator).
 */
class RentalRepositoryAnswersTest {

  private val functions = mockk<FirebaseFunctions>()
  private val callable = mockk<HttpsCallableReference>()
  private val repository =
      RentalRepositoryFirestore(db = mockk<FirebaseFirestore>(), functions = { functions })

  @Before
  fun setUp() {
    every { functions.getHttpsCallable(any()) } returns callable
  }

  @Test
  fun acceptRentalCallsTheAcceptFunctionWithTheAnswer() = runTest {
    val sent = slot<Any>()
    every { callable.call(capture(sent)) } returns Tasks.forResult(mockk<HttpsCallableResult>())

    val result = repository.acceptRental("r1", 60, "Route Cantonale 1", "Rue de Lausanne 10")

    assertTrue(result.isSuccess)
    verifyFunction("acceptRental")
    assertEquals(
        mapOf(
            "rentalId" to "r1",
            "deposit" to 60,
            "pickupAddress" to "Route Cantonale 1",
            "returnAddress" to "Rue de Lausanne 10",
        ),
        sent.captured,
    )
  }

  @Test
  fun declineRentalCallsTheDeclineFunctionWithTheRentalId() = runTest {
    val sent = slot<Any>()
    every { callable.call(capture(sent)) } returns Tasks.forResult(mockk<HttpsCallableResult>())

    val result = repository.declineRental("r1")

    assertTrue(result.isSuccess)
    verifyFunction("declineRental")
    assertEquals(mapOf("rentalId" to "r1"), sent.captured)
  }

  @Test
  fun aRefusalFromTheServerIsAFailure() = runTest {
    // Its constructor is internal to the SDK, so the error is a mock of the same class.
    val refusal = mockk<FirebaseFunctionsException>(relaxed = true)
    every { callable.call(any()) } returns Tasks.forException(refusal)

    val result = repository.declineRental("r1")

    assertTrue(result.exceptionOrNull() is FirebaseFunctionsException)
  }

  private fun verifyFunction(name: String) {
    verify { functions.getHttpsCallable(name) }
  }
}
