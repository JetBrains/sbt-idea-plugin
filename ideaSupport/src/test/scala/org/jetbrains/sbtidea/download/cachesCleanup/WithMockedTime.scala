package org.jetbrains.sbtidea.download.cachesCleanup

import org.jetbrains.sbtidea.download.cachesCleanup.TestUtils.MockedTodayDate
import org.scalatest.{BeforeAndAfterEach, Suite}

import java.time.LocalDate

trait WithMockedTime extends BeforeAndAfterEach { this: Suite =>
  def mockedNow(): LocalDate = MockedTodayDate

  override def beforeEach(): Unit = {
    CleanupUtils.setMockTodayDate(MockedTodayDate)
    super.beforeEach()
  }
}
