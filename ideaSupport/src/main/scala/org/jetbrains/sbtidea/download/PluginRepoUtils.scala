package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.Keys.IntellijPlugin
import sbt.URL

import scala.xml.XML

object PluginRepoUtils {
  private val baseUrl = "https://plugins.jetbrains.com"

  private def getPluginXmlDescriptor(idea: BuildInfo, pluginId: String, channel: String): xml.Elem = {
    val chanStr = if (channel.nonEmpty) s"&channel=$channel" else ""
    val urlStr = s"$baseUrl/plugins/list?pluginId=$pluginId$chanStr&build=${idea.edition.edition}-${idea.buildNumber}"
    val infoUrl = new URL(urlStr)
    XML.load(infoUrl)
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

  def getPluginName(idea: BuildInfo, pluginId: String, channel: String): Either[String, String] = try {
    val xmlData = getPluginXmlDescriptor(idea, pluginId, channel)
    Right((xmlData \\ "name").text)
  } catch {
    case e: Exception => Left(e.getMessage)
  }

  def getLatestPluginVersion(idea: BuildInfo, pluginId: String, channel: String): Either[String, String] = try {
    val xmlData = getPluginXmlDescriptor(idea, pluginId, channel)
    val versionNode = xmlData \\ "version"
    versionNode
      .headOption
      .map(node => Right(node.text))
      .getOrElse(Left(s"Can't find version element: $xmlData"))
  } catch {
    case e: Exception => Left(e.getMessage)
  }
}
