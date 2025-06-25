package org.jetbrains.sbtidea.download.cachesCleanup

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.NioUtils

import java.nio.file.{Files, Path}
import scala.util.{Failure, Success, Try}

class OldDownloadsCleanup(log: PluginLogger) {

  private val SbtKeyName = "autoRemoveOldCachedDownloads"

  /**
   * Detects old cached downloads and removes them if auto-removal is enabled.
   *
   * @param downloadsDir the downloads directory to scan
   * @param autoRemove whether to automatically remove old cached downloads
   */
  def detectOldDownloadsRemoveIfNeeded(
    downloadsDir: Path,
    autoRemove: Boolean
  ): Unit = {
    if (!Files.exists(downloadsDir) || !Files.isDirectory(downloadsDir)) {
      return
    }

    val report = DownloadsCollector.collectDownloads(downloadsDir)
    detectOldDownloadsAndRemoveIfNeeded(report, autoRemove)
  }

  /**
   * Overloaded method that accepts a custom DownloadsReport.
   * This is useful for testing with mocked creation dates.
   */
  private def detectOldDownloadsAndRemoveIfNeeded(
    report: DownloadsReport,
    autoRemove: Boolean
  ): Unit = {
    val oldDownloads = OldDownloadsDetector.detectOldDownloads(report)

    if (oldDownloads.isEmpty) {
      log.debug("No old cached downloads found for cleanup")
      return
    }

    val totalSizeBytes = oldDownloads.map(_.path).map(FileUtils.getFileSize).sum
    val totalSizeFormatted = CleanupUtils.formatSize(totalSizeBytes)
    val filesCount = oldDownloads.length

    val warningMessage =
      s"""Detected $filesCount old cached download files (older than 1 month) in ${report.baseDirectory.toAbsolutePath}
         |Total size: $totalSizeFormatted
         |""".stripMargin.trim

    log.warn(warningMessage)

    if (autoRemove) {
      log.warn(s"Removing old cached download files... (`$SbtKeyName` is enabled)")
      removeOldDownloads(oldDownloads)
      log.info(s"Successfully removed $filesCount old cached download files, freed $totalSizeFormatted of disk space")
    } else {
      log.warn(s"To automatically remove these files, set '$SbtKeyName := true' in your build.sbt")
    }
  }

  private def removeOldDownloads(oldDownloads: Seq[FileMetaInfo]): Unit = {
    oldDownloads.foreach { fileInfo =>
      Try(NioUtils.delete(fileInfo.path)) match {
        case Success(_) =>
          // Don't log individual file removals, only summary
          // This is to avoid spamming, as in average there are more cached downloads compared to SDK directories

        case Failure(exception) =>
          log.warn(s"Failed to remove old cached download ${fileInfo.path.getFileName}: ${exception.getMessage}")
      }
    }
  }

  private def presentDownloadFileWithCreationDateHint(fileInfo: FileMetaInfo): String = {
    val formattedDate = CleanupUtils.DateFormatter.format(fileInfo.metaData.creationDate)
    val ago = CleanupUtils.formatAgo(fileInfo.metaData.creationDate)
    val sizeFormatted = CleanupUtils.formatSize(FileUtils.getFileSize(fileInfo.path))
    s"${fileInfo.path.getFileName} ($sizeFormatted, created: $ago, $formattedDate)"
  }
}