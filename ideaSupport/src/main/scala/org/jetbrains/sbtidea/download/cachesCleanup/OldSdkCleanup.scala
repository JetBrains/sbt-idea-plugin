package org.jetbrains.sbtidea.download.cachesCleanup

import org.jetbrains.sbtidea.PluginLogger

import java.nio.file.Path
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
      Try(FileUtils.deleteDirectory(dir)).failed.foreach { ex =>
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

    val totalSizeBytes: Long = oldSdks.map(sdk => FileUtils.getDirectorySize(sdk.directory)).sum
    val totalSizeFormatted = CleanupUtils.formatSize(totalSizeBytes)

    val otherSdks = sdks.filterNot(oldSdks.contains)

    //first display newer then older
    val oldSdksPresentableList = CleanupUtils.buildPresentableList(oldSdks.sortBy(_.dirInfo.creationDate).reverse, presentSdkVersionWithCreationDateHint)
    val otherSdksPresentableList = CleanupUtils.buildPresentableList(otherSdks.sortBy(_.dirInfo.creationDate).reverse, presentSdkVersionWithCreationDateHint)

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

  private def presentSdkVersionWithCreationDateHint(sdk: IntellijSdkDirInfo): String = {
    val formattedDate = CleanupUtils.DateFormatter.format(sdk.dirInfo.creationDate)
    val ago = CleanupUtils.formatAgo(sdk.dirInfo.creationDate)
    s"${sdk.fullVersion.versionString} (created: $ago, $formattedDate)"
  }
}