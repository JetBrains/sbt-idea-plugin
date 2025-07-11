package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.download.plugin.PluginInfo.PluginDownloadInfo

import java.nio.file.Path

trait PluginIndex {
  /**
   * @param downloadedPluginFileName stores the name of the plugin artifact if the plugin was not present in the
   *                                 unpacked IntelliJ SDK and was downloaded from the marketplace or any other resource
   */
  def put(descriptor: PluginDescriptor, installPath: Path, downloadInfo: Option[PluginDownloadInfo]): Unit

  def contains(id: String): Boolean

  def getInstallRoot(id: String): Option[Path]

  def getPluginDescriptor(id: String): Option[PluginDescriptor]

  /**
   * @return the name of the downloaded plugin file if the plugin was downloaded
   */
  def getDownloadedPluginInfo(id: String): Option[PluginDownloadInfo]

  def getAllDescriptors: Seq[PluginDescriptor]
}
