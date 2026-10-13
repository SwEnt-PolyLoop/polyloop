package com.swent.polyloop.utils

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.Assert.fail

/**
 * Points Firebase Auth, Firestore and Storage at the local Firebase emulators (ports in
 * firebase.json), so tests never touch the real project. Call [connect] first thing in a test's
 * setup.
 */
object FirebaseEmulator {
  // From inside the Android emulator, 10.0.2.2 is the computer running it.
  private const val HOST = "10.0.2.2"
  private const val AUTH_PORT = 9099
  private const val FIRESTORE_PORT = 8080
  private const val STORAGE_PORT = 9199

  private var connected = false

  /** Fails the test with a clear message if the emulators are not running. */
  fun connect() {
    if (!isRunning(AUTH_PORT) || !isRunning(STORAGE_PORT)) {
      fail("The Firebase emulators are not running. Start them with: firebase emulators:start")
    }
    // useEmulator may only be called once per app process, before Firebase is used.
    if (connected) return
    Firebase.auth.useEmulator(HOST, AUTH_PORT)
    Firebase.firestore.useEmulator(HOST, FIRESTORE_PORT)
    Firebase.storage.useEmulator(HOST, STORAGE_PORT)
    connected = true
  }

  /**
   * Marks [email] as verified, like clicking the link in the verification email. The Auth emulator
   * sends no real emails; it lists the codes of the links it would have sent.
   */
  suspend fun verifyEmail(email: String) {
    val projectId = FirebaseApp.getInstance().options.projectId
    val json = URL("http://$HOST:$AUTH_PORT/emulator/v1/projects/$projectId/oobCodes").readText()
    val codes = JSONObject(json).getJSONArray("oobCodes")
    val code =
        (0 until codes.length())
            .map { codes.getJSONObject(it) }
            .last {
              it.getString("email") == email && it.getString("requestType") == "VERIFY_EMAIL"
            }
            .getString("oobCode")
    Firebase.auth.applyActionCode(code).await()
  }

  /** True if an emulator answers on [port]. Any status code counts: only the answer matters. */
  private fun isRunning(port: Int): Boolean =
      try {
        val connection = URL("http://$HOST:$port").openConnection() as HttpURLConnection
        connection.connectTimeout = 2000
        connection.responseCode > 0
      } catch (e: IOException) {
        false
      }
}
