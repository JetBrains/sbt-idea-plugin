package org.jetbrains.sbtidea.download.plugin

import java.net.URL

import org.jetbrains.sbtidea.Keys.IntellijPlugin
import org.jetbrains.sbtidea.download.BuildInfo

trait PluginRepoApi {
  def getRemotePluginXmlDescriptor(idea: BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, PluginDescriptor]
  def getPluginDownloadURL(idea: BuildInfo, pluginInfo: IntellijPlugin.Id): URL
  def getLatestPluginVersion(idea: BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, String]
}
