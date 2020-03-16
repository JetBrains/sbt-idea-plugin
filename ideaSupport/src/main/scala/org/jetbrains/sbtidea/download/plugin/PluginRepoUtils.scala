package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.Keys.IntellijPlugin
import org.jetbrains.sbtidea.download._
import sbt.URL

import scala.util.Try

class PluginRepoUtils extends PluginRepoApi {
  private val baseUrl = "https://plugins.jetbrains.com"

  def getRemotePluginXmlDescriptor(idea: BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, PluginDescriptor] = {
    val chanStr = channel.map(c => s"&channel=$c").getOrElse("")
    val urlStr = s"$baseUrl/plugins/list?pluginId=$pluginId$chanStr&build=${idea.edition.edition}-${idea.buildNumber}"
    val infoUrl = new URL(urlStr)
    Try(PluginDescriptor.load(infoUrl)).toEither
  }

  def getPluginDownloadURL(idea: BuildInfo, pluginInfo: IntellijPlugin.Id): URL = {
    val urlStr = pluginInfo match {
      case IntellijPlugin.Id(id, Some(version), Some(channel)) =>
        s"$baseUrl/plugin/download?pluginId=$id&version=$version&channel=$channel"
      case IntellijPlugin.Id(id, Some(version), None) =>
        s"$baseUrl/plugin/download?pluginId=$id&version=$version"
      case IntellijPlugin.Id(id, None, Some(channel)) =>
        s"$baseUrl/pluginManager?action=download&id=$id&channel=$channel&build=${idea.edition.edition}-${idea.buildNumber}"
      case IntellijPlugin.Id(id, None, None) =>
        s"$baseUrl/pluginManager?action=download&id=$id&build=${idea.edition.edition}-${idea.buildNumber}"
    }
    new URL(urlStr)
  }

  def getLatestPluginVersion(idea: BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, String] =
    getRemotePluginXmlDescriptor(idea, pluginId, channel).map(_.version)
}
