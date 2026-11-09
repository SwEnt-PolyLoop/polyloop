package com.swent.polyloop

import org.junit.Assert.fail
import org.junit.Test

/** Fails on purpose, to check that a failing device test turns CI red (#334). Never merged. */
class CiFailureCheckInstrumentedTest {
  @Test
  fun failsOnPurpose() {
    fail("Failing on purpose to check that CI turns red (#334)")
  }
}
