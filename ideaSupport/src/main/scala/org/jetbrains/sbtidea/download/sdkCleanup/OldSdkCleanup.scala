package org.jetbrains.sbtidea.download.sdkCleanup

import org.apache.commons.io.FileUtils
import org.jetbrains.annotations.TestOnly
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.sdkCleanup.CachedSdksCollector.CachedSdksReport
import org.jetbrains.sbtidea.download.sdkCleanup.OldSdkCleanup.*

import java.nio.file.{Files, Path}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.util.Try

final class OldSdkCleanup(logger: PluginLogger) {

  // Should be same as `org.jetbrains.sbtidea.Keys.autoRemoveOldCachedIntelliJSDK`
  private val SbtKeyName = "autoRemoveOldCachedIntelliJSDK"

  def detectOldSdksRemoveIfNeeded(
    sdksRootDir: Path,
    autoRemove: Boolean,
  ): Unit = {
    val report = CachedSdksCollector.collectSdks(sdksRootDir)
    detectOldSdksRemoveIfNeeded(report, autoRemove)
  }

  /**
   * Overloaded method that accepts a custom CachedSdksReport.
   * This is useful for testing with mocked creation dates.
   */
  def detectOldSdksRemoveIfNeeded(
    cachedSdksReport: CachedSdksReport,
    autoRemove: Boolean,
  ): Unit = {
    detectOldSdksAndBuildWarningMessage(cachedSdksReport) match {
      case Some(cleanupResolution) =>
        logger.warn(cleanupResolution.warningMessage)

        if (autoRemove) {
          logger.warn(s"Removing old SDK directories... (`$SbtKeyName` is enabled)")

          val directories = cleanupResolution.oldSdks.map(_.directory)
          deleteDirectoriesSafe(directories)
        } else {
          logger.warn(s"If you want old SDKs to be automatically removed, use `$SbtKeyName := true` in your build.sbt")
        }
      case _ => //do nothing
    }
  }

  private def deleteDirectoriesSafe(directories: Seq[Path]): Unit = {
    directories.foreach { dir =>
      Try(FileUtils.deleteDirectory(dir.toFile)).failed.foreach { ex =>
        logger.warn(s"Failed to delete $dir ($ex)")
      }
    }
  }

  private case class OldSdksWithWarningMessage(
    oldSdks: Seq[IntellijSdkDirInfo],
    warningMessage: String,
  )

  private def detectOldSdksAndBuildWarningMessage(
    cachedSdksReport: CachedSdksReport,
  ): Option[OldSdksWithWarningMessage] = {
    val sdks = cachedSdksReport.sdkInfos

    val oldSdks = OldSdkDetector.detectOldSdks(sdks)
    if (oldSdks.isEmpty)
      return None

    val totalSizeBytes = oldSdks.map { sdk =>
      try {
        FileUtils.sizeOfDirectory(sdk.directory.toFile)
      } catch {
        case _: java.io.FileNotFoundException => 0L // Handle mock directories that don't exist
        case _: java.io.UncheckedIOException => 0L // Handle mock directories that don't exist
        case _: Exception => 0L // Handle any other exceptions
      }
    }.sum
    val totalSizeFormatted = formatSize(totalSizeBytes)

    val otherSdks = sdks.filterNot(oldSdks.contains)

    //first display newer then older
    val oldSdksPresentableList = buildPresentableList(oldSdks.sortBy(_.dirInfo.creationDate).reverse)
    val otherSdksPresentableList = buildPresentableList(otherSdks.sortBy(_.dirInfo.creationDate).reverse)

    val warningMessage =
      s"""Detected ${oldSdks.size} old IntelliJ SDK directories in ${cachedSdksReport.baseDirectory.toAbsolutePath}
         |Total size: $totalSizeFormatted
         |Old SDKs:
         |$oldSdksPresentableList
         |Remaining SDKs:
         |$otherSdksPresentableList
         |""".stripMargin.trim

    Some(OldSdksWithWarningMessage(oldSdks, warningMessage))
  }
}

object OldSdkCleanup {

  private val DateFormatter = DateTimeFormatter.ofPattern("dd MMM yy")
  private var MockTodayDate: Option[LocalDate] = None

  @TestOnly
  def setMockTodayDate(date: LocalDate): Unit = {
    MockTodayDate = Some(date)
  }

  private def buildPresentableList(sdks: Seq[IntellijSdkDirInfo]): String = {
    val ListSeparator = "• "
    sdks.map(presentSdkVersionWithCreationDateHint).map(ListSeparator + _).mkString("\n").indented(2)
  }

  private def presentSdkVersionWithCreationDateHint(sdk: IntellijSdkDirInfo): String = {
    val formattedDate = DateFormatter.format(sdk.dirInfo.creationDate)
    val ago = formatAgo(sdk.dirInfo.creationDate)
    s"${sdk.fullVersion.versionString} (created: $ago, $formattedDate)"
  }

  /**
   * Formats a given date into a human-readable string indicating how long ago it was
   * (e.g., "2 days ago", "1 week ago", "more than 1 month ago").
   *
   * @param date the date to format, represented as a `LocalDate`
   * @return a string indicating how long ago the given date occurred (e.g., "today", "3 days ago", "more than 2 months ago")
   */
  private def formatAgo(date: LocalDate): String = {
    val now = MockTodayDate.getOrElse(LocalDate.now())

    val daysAgo = ChronoUnit.DAYS.between(date, now)
    val weeksAgo = daysAgo / 7
    val monthsAgo = ChronoUnit.MONTHS.between(date, now)

    if (daysAgo <= 0)
      "today"
    else if (daysAgo < 7)
      s"${pluralize(daysAgo, "day")} ago"
    else if (monthsAgo <= 0)
      s"more than ${pluralize(weeksAgo, "week")} ago"
    else
      s"more than ${pluralize(monthsAgo, "month")} ago"
  }

  /**
   * Formats a size in bytes to a human-readable string
   *
   * @param bytes the size in bytes
   * @return a formatted string (e.g., "1.23 MB")
   */
  private def formatSize(bytes: Long): String = {
    val units = Array("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble
    var unitIndex = 0

    while (size > 1024 && unitIndex < units.length - 1) {
      size /= 1024
      unitIndex += 1
    }

    f"$size%.2f ${units(unitIndex)}"
  }

  private implicit class StringOps(private val str: String) extends AnyVal {
    def indented(indent: Int): String = {
      val indentStr = " " * indent
      str.linesIterator.map(indentStr + _).mkString("\n")
    }
  }
}
