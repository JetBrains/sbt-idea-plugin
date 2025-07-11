package org.jetbrains.sbtidea.download.cachesCleanup

import org.jetbrains.sbtidea.CapturingLogger
import org.jetbrains.sbtidea.download.cachesCleanup.TestUtils.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}

class OldSdkCleanupTest extends AnyFunSuite with Matchers with BeforeAndAfterEach with WithMockedTime {

  // Create a temporary directory for tests
  private var tempDir: Path = _

  override def beforeEach(): Unit = {
    super.beforeEach()
    tempDir = Files.createTempDirectory("old-sdk-cleanup-test")
  }

  override def afterEach(): Unit = {
    if (tempDir != null && Files.exists(tempDir)) {
      FileUtils.deleteDirectory(tempDir)
    }
    super.afterEach()
  }

  private def createMockSdkDirectories(sdkInfos: Seq[IntellijSdkDirInfo]): Seq[Path] = {
    sdkInfos.map { sdk =>
      val dir = tempDir.resolve(sdk.fullVersion.versionString)
      Files.createDirectories(dir)
      dir
    }
  }

  private def createNonEmptySdkDirectories(sdkInfos: Seq[IntellijSdkDirInfo]): Seq[Path] = {
    sdkInfos.map { sdk =>
      val dir = tempDir.resolve(sdk.fullVersion.versionString)
      Files.createDirectories(dir)

      // Create a subdirectory with a dummy file to make the directory non-empty
      val subDir = dir.resolve("subdirectory")
      Files.createDirectories(subDir)
      val dummyFile = subDir.resolve("dummy.txt")
      Files.write(dummyFile, "This is a dummy file".getBytes)

      dir
    }
  }

  private def createMockedCachedSdksReport(sdkInfos: Seq[IntellijSdkDirInfo]): CachedSdksReport = {
    val dirs = createMockSdkDirectories(sdkInfos)

    // Create new SDK infos with the actual paths of the directories we created
    val updatedSdkInfos = sdkInfos.zip(dirs).map { case (sdk, dir) =>
      IntellijSdkDirInfo(
        directory = dir,
        fullVersion = sdk.fullVersion,
        majorVersion = sdk.majorVersion,
        dirInfo = sdk.dirInfo
      )
    }

    CachedSdksReport(updatedSdkInfos, tempDir)
  }

  test("detectOldSdksRemoveIfNeeded - no old SDKs") {
    val mockLogger = new CapturingLogger()

    val recentSdks = Seq(
      createSdkInfoMock(252, 5, 1), // 1 day ago
      createSdkInfoMock(251, 5, 1) // 1 day ago
    )
    val recentDirs = createMockSdkDirectories(recentSdks)

    val mockedReport = createMockedCachedSdksReport(recentSdks)

    new OldSdkCleanup(mockLogger).detectOldSdksRemoveIfNeeded(mockedReport, autoRemove = true)

    mockLogger.getText shouldBe "[debug] No old cached IntelliJ SDK directories found for cleanup"

    recentDirs.foreach { dir =>
      TestUtils.assertDirectoryExists(dir, shouldExist = true)
    }
  }

  test("detectOldSdksRemoveIfNeeded - with old SDKs, autoRemove=false") {
    val mockLogger = new CapturingLogger()

    val oldSdks = Seq(
      createSdkInfoMock(242, 4, 7 * 31), // 7 months ago
      createSdkInfoMock(242, 5, 6 * 31) // 6 months ago
    )
    val recentSdks = Seq(
      createSdkInfoMock(252, 5, 2), // 2 day ago
      createSdkInfoMock(251, 5, 1) // 1 day ago
    )

    val oldDirs = createMockSdkDirectories(oldSdks)
    val recentDirs = createMockSdkDirectories(recentSdks)

    val allSdks = oldSdks ++ recentSdks
    val mockedReport = createMockedCachedSdksReport(allSdks)

    new OldSdkCleanup(mockLogger).detectOldSdksRemoveIfNeeded(mockedReport, autoRemove = false)

    // Normalize the warning message and check the entire string
    val normalizedWarning = normalizeWarningMessage(mockLogger.getText)

    // Test the warning message with a single assertion
    normalizedWarning shouldBe
      s"""[warn] Detected 2 old IntelliJ SDK directories in ${tempDir.toAbsolutePath}
         |[warn] Total size: SIZE_PLACEHOLDER
         |[warn] Old SDKs:
         |[warn]   • 242.5 (created: more than 6 months ago, 25 Nov 24)
         |[warn]   • 242.4 (created: more than 7 months ago, 25 Oct 24)
         |[warn] Remaining SDKs:
         |[warn]   • 251.5 (created: 1 day ago, 29 May 25)
         |[warn]   • 252.5 (created: 2 days ago, 28 May 25)
         |[warn] If you want old SDKs to be automatically removed, use `autoRemoveOldCachedIntelliJSDK := true` in your build.sbt
         |""".stripMargin.trim

    // Verify all directories still exist (since autoRemove=false)
    assertDirectoriesExist(oldDirs ++ recentDirs, shouldExist = true)
  }

  test("detectOldSdksRemoveIfNeeded - with old SDKs, autoRemove=true") {
    val mockLogger = new CapturingLogger()

    // Create old SDK directories using TestUtils.createSdkInfoMock
    val oldSdks = Seq(
      createSdkInfoMock(242, 4, 7 * 31), // 7 months ago
      createSdkInfoMock(242, 5, 6 * 31) // 6 months ago
    )
    val oldDirs = createNonEmptySdkDirectories(oldSdks)

    // Create recent SDK directories using TestUtils.createSdkInfoMock
    val recentSdks = Seq(
      createSdkInfoMock(252, 5, 2), // 2 days ago
      createSdkInfoMock(251, 5, 1) // 1 day ago
    )
    val recentDirs = createMockSdkDirectories(recentSdks)

    val allSdks = oldSdks ++ recentSdks
    val mockedReport = createMockedCachedSdksReport(allSdks)
    new OldSdkCleanup(mockLogger).detectOldSdksRemoveIfNeeded(mockedReport, autoRemove = true)

    // Normalize the warning message and check the entire string
    val normalizedWarning = normalizeWarningMessage(mockLogger.getText)

    normalizedWarning shouldBe
      s"""[warn] Detected 2 old IntelliJ SDK directories in ${tempDir.toAbsolutePath}
         |[warn] Total size: SIZE_PLACEHOLDER
         |[warn] Old SDKs:
         |[warn]   • 242.5 (created: more than 6 months ago, 25 Nov 24)
         |[warn]   • 242.4 (created: more than 7 months ago, 25 Oct 24)
         |[warn] Remaining SDKs:
         |[warn]   • 251.5 (created: 1 day ago, 29 May 25)
         |[warn]   • 252.5 (created: 2 days ago, 28 May 25)
         |[warn] Removing old SDK directories... (`autoRemoveOldCachedIntelliJSDK` is enabled)
         |""".stripMargin.trim

    // Verify old directories were removed
    assertDirectoriesExist(oldDirs, shouldExist = false)

    // Verify recent directories still exist
    assertDirectoriesExist(recentDirs, shouldExist = true)
  }

  test("detectOldSdksRemoveIfNeeded - mixed old and recent versions") {
    val mockLogger = new CapturingLogger()

    // Create a mix of old and recent SDK directories for different major versions using TestUtils.createSdkInfoMock
    val oldSdks = Seq(
      createSdkInfoMock(242, 4, 7 * 31), // ~7 months ago - Old major version (242)
      createSdkInfoMock(242, 5, 6 * 31), // ~6 months ago - Old major version (242)
      createSdkInfoMock(243, 1, 4 * 31), // ~4 months ago - Previous major version, old minor versions
      createSdkInfoMock(243, 2, 3 * 31), // ~3 months ago - Previous major version, old minor versions
      createSdkInfoMock(251, 1, 3 * 31), // ~3 months ago - Current major version, old minor versions
      createSdkInfoMock(251, 2, 2 * 31) // ~2 months ago - Current major version, old minor versions
    )
    val oldDirs = createMockSdkDirectories(oldSdks)

    // Recent versions that should be kept using TestUtils.createSdkInfoMock
    val recentSdks = Seq(
      createSdkInfoMock(243, 5, 1 * 30), // ~1 month ago - Latest for the previous major version
      createSdkInfoMock(251, 5, 2), // 2 days ago - Latest for the current major version
      createSdkInfoMock(252, 5, 1) // 1 day ago - Latest for the current major version
    )
    val recentDirs = createMockSdkDirectories(recentSdks)

    val allSdks = oldSdks ++ recentSdks
    val mockedReport = createMockedCachedSdksReport(allSdks)

    new OldSdkCleanup(mockLogger).detectOldSdksRemoveIfNeeded(mockedReport, autoRemove = true)

    val normalizedWarning = normalizeWarningMessage(mockLogger.getText)
    normalizedWarning shouldBe
      s"""[warn] Detected 6 old IntelliJ SDK directories in ${tempDir.toAbsolutePath}
         |[warn] Total size: SIZE_PLACEHOLDER
         |[warn] Old SDKs:
         |[warn]   • 251.2 (created: more than 2 months ago, 29 Mar 25)
         |[warn]   • 243.2 (created: more than 3 months ago, 26 Feb 25)
         |[warn]   • 251.1 (created: more than 3 months ago, 26 Feb 25)
         |[warn]   • 243.1 (created: more than 4 months ago, 26 Jan 25)
         |[warn]   • 242.5 (created: more than 6 months ago, 25 Nov 24)
         |[warn]   • 242.4 (created: more than 7 months ago, 25 Oct 24)
         |[warn] Remaining SDKs:
         |[warn]   • 252.5 (created: 1 day ago, 29 May 25)
         |[warn]   • 251.5 (created: 2 days ago, 28 May 25)
         |[warn]   • 243.5 (created: more than 1 month ago, 30 Apr 25)
         |[warn] Removing old SDK directories... (`autoRemoveOldCachedIntelliJSDK` is enabled)
         |""".stripMargin.trim

    // Verify old directories were removed
    assertDirectoriesExist(oldDirs, shouldExist = false)

    // Verify recent directories still exist
    assertDirectoriesExist(recentDirs, shouldExist = true)
  }
}
