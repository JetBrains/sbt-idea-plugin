package org.jetbrains.sbtidea.download

import java.nio.file.Path
import java.time.LocalDate

package object sdkCleanup {

  private[download]
  implicit val localDateOrdering: Ordering[LocalDate] = _.compareTo(_)

  private[download]
  def pluralize(count: Long, singular: String): String =
    s"$count $singular${if (count == 1) "" else "s"}"

  /**
   * @param creationDate date when the directory was created on the file system
   */
  private[download]
  case class DirectoryMetaData(
    creationDate: LocalDate
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
}
