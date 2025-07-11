package org.jetbrains.sbtidea.download.plugin
import org.jetbrains.sbtidea.IntellijPlugin
import org.jetbrains.sbtidea.download.plugin.PluginInfo.PluginDownloadInfo

import java.nio.file.Path

trait LocalPluginRegistryApi {

  def getPluginDescriptor(ideaPlugin: IntellijPlugin): Either[String, PluginDescriptor]

  def markPluginInstalled(ideaPlugin: IntellijPlugin, to: Path): Unit

  def markPluginInstalled(ideaPlugin: IntellijPlugin, to: Path, downloadInfo: Option[PluginDownloadInfo]): Unit

  def isPluginInstalled(ideaPlugin: IntellijPlugin): Boolean

  def getInstalledPluginRoot(ideaPlugin: IntellijPlugin): Path

  def getDownloadedPluginInfo(ideaPlugin: IntellijPlugin): Option[PluginDownloadInfo]

  def isDownloadedPlugin(ideaPlugin: IntellijPlugin): Boolean

  def getAllDescriptors: Seq[PluginDescriptor]
}
