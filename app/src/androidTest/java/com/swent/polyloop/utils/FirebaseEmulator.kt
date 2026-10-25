// Edited with Claude.

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
import org.json.JSONArray
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
  private const val TIMEOUT_MS = 2000
  private const val REQUEST_TIMEOUT_MS = 10_000

  /** True once Firebase points at the emulators, for the rest of the app process. */
  var connected = false
    private set

  /** Fails the test with a clear message if the emulators are not running. */
  fun connect() {
    if (!listOf(AUTH_PORT, FIRESTORE_PORT, STORAGE_PORT).all { isRunning(it) }) {
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
    val json = request("GET", "http://$HOST:$AUTH_PORT/emulator/v1/projects/$projectId/oobCodes")
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

  /**
   * Creates the document at [path] (e.g. "users/abc") with [fields], skipping the Security Rules as
   * an admin would. For test data that belongs to another user, which the app may not write.
   */
  fun createDocumentAsAdmin(path: String, fields: Map<String, Any?>) {
    val collection = path.substringBeforeLast('/')
    val id = path.substringAfterLast('/')
    val body = JSONObject().put("fields", toFields(fields))
    request("POST", "${documentsUrl()}/$collection?documentId=$id", body.toString(), asAdmin = true)
  }

  /**
   * Deletes the document at [path], skipping the Security Rules. A missing document is no error.
   */
  fun deleteDocumentAsAdmin(path: String) {
    request("DELETE", "${documentsUrl()}/$path", asAdmin = true)
  }

  private fun documentsUrl(): String {
    val projectId = FirebaseApp.getInstance().options.projectId
    return "http://$HOST:$FIRESTORE_PORT/v1/projects/$projectId/databases/(default)/documents"
  }

  /** Sends a bounded HTTP request to an emulator and returns the response body. */
  private fun request(
      method: String,
      url: String,
      body: String? = null,
      asAdmin: Boolean = false,
  ): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = REQUEST_TIMEOUT_MS
    connection.readTimeout = REQUEST_TIMEOUT_MS
    // The Firestore emulator lets this token skip the Security Rules.
    if (asAdmin) connection.setRequestProperty("Authorization", "Bearer owner")
    return try {
      if (body != null) {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
      }
      val code = connection.responseCode
      check(code in 200..299) {
        "$method $url failed ($code): ${connection.errorStream?.bufferedReader()?.readText()}"
      }
      connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
      connection.disconnect()
    }
  }

  /** The fields of a Firestore REST document. Covers the types the tests write. */
  private fun toFields(fields: Map<String, Any?>): JSONObject =
      JSONObject().apply { fields.forEach { (name, value) -> put(name, toValue(value)) } }

  private fun toValue(value: Any?): JSONObject =
      when (value) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is String -> JSONObject().put("stringValue", value)
        is Boolean -> JSONObject().put("booleanValue", value)
        is Int,
        is Long -> JSONObject().put("integerValue", value.toString())
        is List<*> ->
            JSONObject()
                .put("arrayValue", JSONObject().put("values", JSONArray(value.map(::toValue))))
        is Map<*, *> ->
            JSONObject()
                .put(
                    "mapValue",
                    JSONObject().put("fields", toFields(value.mapKeys { it.key.toString() })),
                )
        else -> throw IllegalArgumentException("Unsupported field value: $value")
      }

  /** True if an emulator answers on [port]. Any status code counts: only the answer matters. */
  private fun isRunning(port: Int): Boolean {
    val connection = URL("http://$HOST:$port").openConnection() as HttpURLConnection
    // Both timeouts, so a port that accepts the connection but never answers fails fast.
    connection.connectTimeout = TIMEOUT_MS
    connection.readTimeout = TIMEOUT_MS
    return try {
      connection.responseCode > 0
    } catch (e: IOException) {
      false
    } finally {
      connection.disconnect()
    }
  }
}
