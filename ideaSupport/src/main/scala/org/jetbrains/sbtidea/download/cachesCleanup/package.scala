package org.jetbrains.sbtidea.download

import java.nio.file.Path
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.FiniteDuration

package object cachesCleanup {

  private[download]
  implicit val localDateOrdering: Ordering[LocalDate] = _.compareTo(_)

  /**
   * @param creationDate date when the directory was created on the file system
   */
  private[download]
  case class DirectoryMetaData(
    creationDate: LocalDate
  )

  /**
   * General representation of a file or directory with metadata for cleanup purposes
   *
   * @param path the path to the file or directory
   * @param metaData the metadata about the file/directory (creation date, etc.)
   */
  private[download]
  case class FileMetaInfo(
    path: Path,
    metaData: DirectoryMetaData
  )

  /**
   * Represents an IntelliJ SDK with its version information and directory details
   *
   * @param directory    the directory containing the SDK
   * @param fullVersion  the full version string (e.g., "251.26094.105")
   * @param majorVersion the major version number (e.g., 251)
   * @param dirInfo      the directory information (size, creation date)
   */
  private[download]
  case class IntellijSdkDirInfo(
    directory: Path,
    fullVersion: Version,
    majorVersion: Int,
    dirInfo: DirectoryMetaData
  )

  /**
   * Represents a collection of IntelliJ SDKs found in a directory
   */
  private[download]
  case class CachedSdksReport(
    sdkInfos: Seq[IntellijSdkDirInfo],
    baseDirectory: Path,
  )

  /**
   * Represents a collection of download files found in a directory
   */
  private[download]
  case class DownloadsReport(
    fileInfos: Seq[FileMetaInfo],
    baseDirectory: Path
  )


  private[download]
  def isOlderThan(date: LocalDate, duration: FiniteDuration): Boolean = {
    val now = CleanupUtils.cleanupRelevantTime()
    val diff = ChronoUnit.DAYS.between(date, now)
    diff > duration.toDays
  }
}