package org.jetbrains.sbtidea.download.cachesCleanup

import org.jetbrains.sbtidea.download.Version

import java.nio.file.{Files, Path, Paths}
import java.time.LocalDate

object TestUtils {

  // Mock the current date to be 30 May 2025
  val MockedTodayDate: LocalDate = LocalDate.parse("2025-05-30")

  def createSdkInfoMock(majorVersion: Int, minorVersion: Int, daysAgo: Int): IntellijSdkDirInfo = {
    val versionString = s"$majorVersion.$minorVersion"
    val creationDate = MockedTodayDate.minusDays(daysAgo)
    val dirInfo = DirectoryMetaData(
      creationDate = creationDate
    )

    IntellijSdkDirInfo(
      directory = Paths.get(s"/mock/path/$versionString"),
      fullVersion = Version(versionString),
      majorVersion = majorVersion,
      dirInfo = dirInfo
    )
  }

  def assertDirectoryExists(dir: Path, shouldExist: Boolean): Unit = {
    val exists = Files.exists(dir)
    val message = if (shouldExist) s"Directory should exist: $dir" else s"Directory should not exist: $dir"
    assert(exists == shouldExist, message)
  }

  def assertFileExists(file: Path, shouldExist: Boolean): Unit = {
    val exists = Files.exists(file)
    val message = if (shouldExist) s"File should exist: $file" else s"File should not exist: $file"
    assert(exists == shouldExist, message)
  }

  def assertFilesExist(files: Seq[Path], shouldExist: Boolean): Unit = {
    files.foreach { file =>
      assertFileExists(file, shouldExist)
    }
  }

  def assertDirectoriesExist(dirs: Seq[Path], shouldExist: Boolean): Unit = {
    dirs.foreach { dir =>
      assertDirectoryExists(dir, shouldExist)
    }
  }

  def normalizeWarningMessage(message: String): String =
    message.replaceAll("Total size: [0-9,.]+\\s*[KMGT]?B", "Total size: SIZE_PLACEHOLDER")

  def createFileMetaInfo(file: Path, daysAgo: Int): FileMetaInfo = {
    val creationDate = MockedTodayDate.minusDays(daysAgo)
    FileMetaInfo(file, DirectoryMetaData(creationDate))
  }

  def createMockDownloadFile(tempDir: Path, fileName: String, daysAgo: Int): Path = {
    val file = tempDir.resolve(fileName)
    Files.write(file, "This is a mock download file".getBytes)
    file
  }
}
