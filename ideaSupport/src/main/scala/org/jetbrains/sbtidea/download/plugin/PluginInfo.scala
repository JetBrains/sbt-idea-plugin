package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.download.plugin.PluginInfo.PluginDownloadInfo

import java.net.URL
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * Represents information about a plugin in the plugin index.
 *
 * @param installPath        the path where the plugin is installed
 * @param descriptor         the plugin descriptor
 * @param downloadInfo       the plugin download info, if the plugin was downloaded, mostly for debugging purposes
 */
case class PluginInfo(
  installPath: Path,
  descriptor: PluginDescriptor,
  downloadInfo: Option[PluginDownloadInfo],
) {
  def withAbsoluteInstallPath(ideaRoot: Path): PluginInfo =
    copy(installPath = ideaRoot.resolve(installPath))

  def withRelativeInstallPath(ideaRoot: Path): PluginInfo =
    copy(installPath = ideaRoot.relativize(installPath))
}

object PluginInfo {

  case class PluginDownloadInfo(
    downloadedFileName: String,
    downloadedUrl: URL,
    downloadedTime: LocalDateTime,
  )
}
