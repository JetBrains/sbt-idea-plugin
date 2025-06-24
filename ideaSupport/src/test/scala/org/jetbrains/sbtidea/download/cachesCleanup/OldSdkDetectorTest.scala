package org.jetbrains.sbtidea.download.cachesCleanup

import org.jetbrains.sbtidea.download.cachesCleanup.TestUtils.createSdkInfoMock
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class OldSdkDetectorTest extends AnyFunSuite with Matchers {

  test("detectOldSdks") {
    val sdkInfos = Seq(
      // 242 major version (older than 2 releases from the latest release)
      createSdkInfoMock(242, 4, 7 * 30), // 7 months ago
      createSdkInfoMock(242, 5, 6 * 30), // 6 months ago

      // 243 major version (previous release)
      createSdkInfoMock(243, 4, 3 * 30), // 3 months ago
      createSdkInfoMock(243, 5, 1 * 30), // 1 months ago, but kept as latest for the previous release (it's less than 2 month old)

      // 251 major version (current release - actively developed)
      createSdkInfoMock(251, 1, 3 * 30), // 3 months ago
      createSdkInfoMock(251, 2, 2 * 30), // 2 months ago
      createSdkInfoMock(251, 3, 20), // 20 days ago
      createSdkInfoMock(251, 4, 10), // 10 days ago
      createSdkInfoMock(251, 5, 1), // 1 day ago

      // 252 major version (current release - actively developed)
      createSdkInfoMock(252, 1, 3 * 30), // 3 months ago
      createSdkInfoMock(252, 2, 2 * 30), // 2 months ago
      createSdkInfoMock(252, 3, 20), // 20 days ago
      createSdkInfoMock(252, 4, 10), // 10 days ago
      createSdkInfoMock(252, 5, 1), // 1 day ago
    )

    val oldSdks = OldSdkDetector.detectOldSdks(sdkInfos)

    val oldSdkVersions = oldSdks.map(_.fullVersion.versionString)

    val expectedOldVersions = Seq(
      "242.4", "242.5", // older than 2 releases from the latest release
      "243.4", // only the latest version (243.5) is kept for the previous release
      "251.1", "251.2", "251.3", // older than 2 weeks
      "252.1", "252.2", "252.3", // older than 2 weeks
    )

    oldSdkVersions.sorted shouldBe expectedOldVersions.sorted
  }

  test("detectOldSdks - keep 2 latest versions not older than 2 months, remove older") {
    val sdkInfos = Seq(
      // 243 major version (previous release - not actively developed)
      createSdkInfoMock(243, 1, 4 * 30), // 4 months ago
      createSdkInfoMock(243, 2, 3 * 30), // 3 months ago
      createSdkInfoMock(243, 3, 1 * 30), // 1 months ago

      // 251 major version (current release - actively developed)
      createSdkInfoMock(251, 1, 3 * 30), // 3 months ago
      createSdkInfoMock(251, 2, 3 * 30), // 3 months ago
      createSdkInfoMock(251, 3, 3 * 30), // 3 months ago

      // 252 major version (current release - actively developed)
      createSdkInfoMock(252, 1, 3 * 30), // 3 months ago
      createSdkInfoMock(252, 2, 2 * 30), // 2 months ago
      createSdkInfoMock(252, 3, 20), // 20 days ago
      createSdkInfoMock(252, 4, 1), // 16 days ago
      createSdkInfoMock(252, 5, 1), // 15 days ago
    )

    val oldSdks = OldSdkDetector.detectOldSdks(sdkInfos)

    val oldSdkVersions = oldSdks.map(_.fullVersion.versionString)

    val expectedOldVersions = Seq(
      "243.1", "243.2", // older than 2 months
      //243.3 // older than 1 month, but it is the latest minor versions that is not older than 2 months
      "251.1", "251.2", "251.3", // older than 2 months
      "252.1", "252.2", "252.3", // older than 2 weeks
      //"252.4", "252.5" // older than 2 weeks, but these are the latest versions, and they are not older than 2 months
    )

    oldSdkVersions.sorted shouldBe expectedOldVersions.sorted
  }

}
