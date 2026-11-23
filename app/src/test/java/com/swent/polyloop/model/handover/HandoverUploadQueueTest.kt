// Made with Claude.

package com.swent.polyloop.model.handover

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HandoverUploadQueueTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private lateinit var queue: HandoverUploadQueue

  // What every job returns once the phone is "online", instead of sending to Firebase.
  private var result: ListenableWorker.Result = ListenableWorker.Result.success()

  @Before
  fun setUp() {
    val factory =
        object : WorkerFactory() {
          override fun createWorker(c: Context, name: String, params: WorkerParameters) =
              object : CoroutineWorker(c, params) {
                override suspend fun doWork() = result
              }
        }
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(factory)
            .build(),
    )
    queue = HandoverUploadQueue(context)
  }

  private fun enqueue(slotId: String, phase: HandoverPhase = HandoverPhase.PICKUP) {
    val photo = File(context.cacheDir, "camera-$slotId.jpg").apply { writeBytes(byteArrayOf(7)) }
    runBlocking { queue.enqueue("rental1", phase, slotId, Instant.ofEpochMilli(TAKEN_AT), photo) }
    photo.delete() // the screen deletes the camera's file once it is queued
  }

  private fun goOnline() {
    val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
    runBlocking { WorkManager.getInstance(context).getWorkInfosByTagFlow(ALL).first() }
        .forEach { driver.setAllConstraintsMet(it.id) }
  }

  private fun observe() = runBlocking { queue.observe("rental1", HandoverPhase.PICKUP).first() }

  @Test
  fun aQueuedPhotoIsKeptAndListedUntilItIsSent() {
    enqueue("me_PICKUP_0")
    enqueue("me_PICKUP_0") // queued twice: still one photo
    enqueue("me_RETURN_0", HandoverPhase.RETURN) // another phase: not listed

    val waiting = observe().single()
    assertEquals("me_PICKUP_0", waiting.slotId)
    assertEquals(Instant.ofEpochMilli(TAKEN_AT), waiting.takenAt)
    assertEquals(false, waiting.refused)
    assertTrue(waiting.file.exists()) // its own copy, kept after the camera's file is gone

    goOnline()
    assertEquals(emptyList<QueuedPhoto>(), observe())
  }

  @Test
  fun aPhotoTheServerRefusedIsListedAsRefused() {
    result = ListenableWorker.Result.failure(workDataOf(HandoverPhotoWorker.REFUSED to true))
    enqueue("me_PICKUP_0")

    goOnline()
    assertTrue(observe().single().refused)
  }

  private companion object {
    const val TAKEN_AT = 1_000L
    // Every job is a HandoverPhotoWorker, whose class name WorkManager adds as a tag.
    val ALL: String = HandoverPhotoWorker::class.java.name
  }
}
