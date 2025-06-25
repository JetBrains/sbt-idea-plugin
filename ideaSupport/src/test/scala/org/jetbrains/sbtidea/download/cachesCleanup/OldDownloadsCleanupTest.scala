package org.jetbrains.sbtidea.download.cachesCleanup

import org.jetbrains.sbtidea.download.cachesCleanup.TestUtils._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}

class OldDownloadsCleanupTest extends AnyFunSuite with Matchers with BeforeAndAfterEach {

  // Create a temporary directory for tests
  private var tempDir: Path = _

  override def beforeEach(): Unit = {
    CleanupUtils.setMockTodayDate(MockedTodayDate)
    tempDir = Files.createTempDirectory("old-downloads-cleanup-test")
  }

  override def afterEach(): Unit = {
    if (tempDir != null && Files.exists(tempDir)) {
      FileUtils.deleteDirectory(tempDir)
    }
  }

  private def createMockedDownloadsReport(fileInfos: Seq[FileMetaInfo]): DownloadsReport = {
    DownloadsReport(fileInfos, tempDir)
  }

  // Helper method to access the private method for testing
  private def invokeDetectOldDownloadsAndRemoveIfNeeded(
    cleanup: OldDownloadsCleanup,
    report: DownloadsReport,
    autoRemove: Boolean
  ): Unit = {
    val method = classOf[OldDownloadsCleanup].getDeclaredMethod(
      "detectOldDownloadsAndRemoveIfNeeded",
      classOf[DownloadsReport],
      classOf[Boolean]
    )
    method.setAccessible(true)
    method.invoke(cleanup, report, java.lang.Boolean.valueOf(autoRemove))
  }

  test("detectOldDownloadsRemoveIfNeeded - no old downloads") {
    val mockLogger = new CapturingTestLogger()

    // Create recent download files
    val recentFile1 = createMockDownloadFile(tempDir, "recent-file1.zip", 15)
    val recentFile2 = createMockDownloadFile(tempDir, "recent-file2.zip", 20)

    // Create FileMetaInfo objects with recent dates
    val recentFileInfo1 = createFileMetaInfo(recentFile1, 15)
    val recentFileInfo2 = createFileMetaInfo(recentFile2, 20)

    val recentFiles = Seq(recentFile1, recentFile2)
    val recentFileInfos = Seq(recentFileInfo1, recentFileInfo2)

    val mockedReport = createMockedDownloadsReport(recentFileInfos)

    val cleanup = new OldDownloadsCleanup(mockLogger)
    invokeDetectOldDownloadsAndRemoveIfNeeded(cleanup, mockedReport, autoRemove = true)

    mockLogger.getLoggedText shouldBe "[debug] No old cached downloads found for cleanup"

    // Verify recent files still exist
    assertFilesExist(recentFiles, shouldExist = true)
  }

  test("detectOldDownloadsRemoveIfNeeded - with old downloads, autoRemove=false") {
    val mockLogger = new CapturingTestLogger()

    // Create old download files
    val oldFile1 = createMockDownloadFile(tempDir, "old-file1.zip", 40)
    val oldFile2 = createMockDownloadFile(tempDir, "old-file2.zip", 60)

    // Create recent download files
    val recentFile1 = createMockDownloadFile(tempDir, "recent-file1.zip", 15)
    val recentFile2 = createMockDownloadFile(tempDir, "recent-file2.zip", 20)

    // Create FileMetaInfo objects with appropriate dates
    val oldFileInfo1 = createFileMetaInfo(oldFile1, 40)
    val oldFileInfo2 = createFileMetaInfo(oldFile2, 60)
    val recentFileInfo1 = createFileMetaInfo(recentFile1, 15)
    val recentFileInfo2 = createFileMetaInfo(recentFile2, 20)

    val allFiles = Seq(oldFile1, oldFile2, recentFile1, recentFile2)
    val allFileInfos = Seq(oldFileInfo1, oldFileInfo2, recentFileInfo1, recentFileInfo2)

    val mockedReport = createMockedDownloadsReport(allFileInfos)

    val cleanup = new OldDownloadsCleanup(mockLogger)
    invokeDetectOldDownloadsAndRemoveIfNeeded(cleanup, mockedReport, autoRemove = false)

    // Normalize the warning message and check it
    val normalizedWarning = normalizeWarningMessage(mockLogger.getLoggedText)

    // Test the warning message with a single assertion
    normalizedWarning shouldBe
      s"""[warn] Detected 2 old cached download files (older than 1 month) in ${tempDir.toAbsolutePath}
         |[warn] Total size: SIZE_PLACEHOLDER
         |[warn] To automatically remove these files, set 'autoRemoveOldCachedDownloads := true' in your build.sbt
         |""".stripMargin.trim

    // Verify all files still exist (since autoRemove=false)
    assertFilesExist(allFiles, shouldExist = true)
  }

  test("detectOldDownloadsRemoveIfNeeded - with old downloads, autoRemove=true") {
    val mockLogger = new CapturingTestLogger()

    // Create old download files
    val oldFile1 = createMockDownloadFile(tempDir, "old-file1.zip", 40)
    val oldFile2 = createMockDownloadFile(tempDir, "old-file2.zip", 60)

    // Create recent download files
    val recentFile1 = createMockDownloadFile(tempDir, "recent-file1.zip", 15)
    val recentFile2 = createMockDownloadFile(tempDir, "recent-file2.zip", 20)

    // Create FileMetaInfo objects with appropriate dates
    val oldFileInfo1 = createFileMetaInfo(oldFile1, 40)
    val oldFileInfo2 = createFileMetaInfo(oldFile2, 60)
    val recentFileInfo1 = createFileMetaInfo(recentFile1, 15)
    val recentFileInfo2 = createFileMetaInfo(recentFile2, 20)

    val oldFiles = Seq(oldFile1, oldFile2)
    val recentFiles = Seq(recentFile1, recentFile2)
    val allFileInfos = Seq(oldFileInfo1, oldFileInfo2, recentFileInfo1, recentFileInfo2)

    val mockedReport = createMockedDownloadsReport(allFileInfos)

    val cleanup = new OldDownloadsCleanup(mockLogger)
    invokeDetectOldDownloadsAndRemoveIfNeeded(cleanup, mockedReport, autoRemove = true)

    // Normalize the warning message and check it
    val normalizedWarning = normalizeWarningMessage(mockLogger.getLoggedText)

    // Test the warning message with a single assertion
    normalizedWarning shouldBe
      s"""[warn] Detected 2 old cached download files (older than 1 month) in ${tempDir.toAbsolutePath}
         |[warn] Total size: SIZE_PLACEHOLDER
         |[warn] Removing old cached download files... (`autoRemoveOldCachedDownloads` is enabled)
         |[info] Successfully removed 2 old cached download files, freed 56.00 B of disk space
         |""".stripMargin.trim

    // Verify old files were removed
    assertFilesExist(oldFiles, shouldExist = false)

    // Verify recent files still exist
    assertFilesExist(recentFiles, shouldExist = true)
  }
}