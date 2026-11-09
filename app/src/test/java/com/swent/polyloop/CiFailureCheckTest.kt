package com.swent.polyloop

import org.junit.Assert.fail
import org.junit.Test

/** Fails on purpose, to check that a failing unit test turns CI red (#334). Never merged. */
class CiFailureCheckTest {
  @Test
  fun failsOnPurpose() {
    fail("Failing on purpose to check that CI turns red (#334)")
  }
}
