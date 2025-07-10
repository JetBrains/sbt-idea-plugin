package org.jetbrains.sbtidea.download.cachesCleanup

import scala.concurrent.duration.DurationInt

object OldDownloadsDetector {

  private val RetentionDays = 30.days // ~1 months

  /**
   * Detects old downloads that are eligible for removal.
   * Simple age-based filter - any file older than 1 month is considered old.
   *
   * @param report the download report containing file information
   * @return sequence of file infos that should be removed
   */
  def detectOldDownloads(report: DownloadsReport): Seq[FileMetaInfo] = {
    report.fileInfos.filter { fileInfo =>
      isOlderThan(fileInfo.metaData.creationDate, RetentionDays)
    }
  }
}