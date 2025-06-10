package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.IntellijPlugin
import org.jetbrains.sbtidea.download.BuildInfo

import java.net.URL

/**
 * Defines API for managing and retrieving plugin-related data from a remote repository (Marketplace)
 */
trait PluginRepoApi {
  def getRemotePluginXmlDescriptor(idea: BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, PluginDescriptor]
  def getPluginDownloadURL(idea: BuildInfo, pluginInfo: IntellijPlugin.Id): URL
  def getLatestPluginVersion(idea: BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, String]
}
