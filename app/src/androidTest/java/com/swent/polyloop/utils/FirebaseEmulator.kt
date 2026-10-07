package com.swent.polyloop.utils

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.fail

/**
 * Points Firebase Auth and Firestore at the local Firebase emulators (ports in firebase.json), so
 * tests never touch the real project. Call [connect] first thing in a test's setup.
 */
object FirebaseEmulator {
  // From inside the Android emulator, 10.0.2.2 is the computer running it.
  private const val HOST = "10.0.2.2"
  private const val AUTH_PORT = 9099
  private const val FIRESTORE_PORT = 8080

  private var connected = false

  /** Fails the test with a clear message if the emulators are not running. */
  fun connect() {
    if (!isRunning()) {
      fail("The Firebase emulators are not running. Start them with: firebase emulators:start")
    }
    // useEmulator may only be called once per app process, before Firebase is used.
    if (connected) return
    Firebase.auth.useEmulator(HOST, AUTH_PORT)
    Firebase.firestore.useEmulator(HOST, FIRESTORE_PORT)
    connected = true
  }

  private fun isRunning(): Boolean =
      try {
        val connection = URL("http://$HOST:$AUTH_PORT").openConnection() as HttpURLConnection
        connection.connectTimeout = 2000
        connection.responseCode == HttpURLConnection.HTTP_OK
      } catch (e: IOException) {
        false
      }
}
