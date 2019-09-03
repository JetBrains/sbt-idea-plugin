package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.Keys.IdeaPlugin
import org.jetbrains.sbtidea.download.api.IdeaArtifactResolver
import sbt.URL

import scala.xml.XML

object PluginRepoUtils {
  private val managerBase = "https://plugins.jetbrains.com/pluginManager"
  private val pluginsBase = "https://plugins.jetbrains.com/plugins"

  private def chan(pluginInfo: IdeaPlugin.Id) = pluginInfo.channel.map(c => s"&channel=$c").getOrElse("")

  def getPluginDownloadURL(idea: BuildInfo, pluginInfo: IdeaPlugin.Id): URL = {
    val urlStr = s"$managerBase?action=download&id=${pluginInfo.id}${chan(pluginInfo)}&build=${idea.edition.shortname}-${idea.buildNumber}"
    new URL(urlStr)
  }

  def getLatestPluginVersion(idea: BuildInfo, pluginId: String, channel: String): Either[String, String] = {
    val chanStr = if (channel.nonEmpty) s"&channel=$channel" else ""
    val urlStr = s"$pluginsBase/list?pluginId=$pluginId$chanStr&build=${idea.edition.shortname}-${idea.buildNumber}"
    val infoUrl = new URL(urlStr)
    try {
      val xmlData = XML.load(infoUrl)
      val versionNode = xmlData \\ "version"
      versionNode
        .headOption
        .map(node => Right(node.text))
        .getOrElse(Left(s"Can't find version element: $xmlData"))
    } catch {
      case e: Exception => Left(e.getMessage)
    }
  }
}
