package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.IntellijPlugin
import org.jetbrains.sbtidea.download.*
import org.jetbrains.sbtidea.download.api.InstallContext
import sbt.URL

import scala.util.{Failure, Success, Try}

class PluginRepoUtils(implicit ctx: InstallContext) extends PluginRepoApi {
  private val baseUrl = "https://plugins.jetbrains.com"

  def getRemotePluginXmlDescriptor(idea: BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, PluginDescriptor] = {
    val chanStr = channel.map(c => s"&channel=$c").getOrElse("")
    val urlStr = s"$baseUrl/plugins/list?pluginId=$pluginId$chanStr&build=${idea.edition.edition}-${idea.getActualIdeaBuild(ctx.baseDirectory)}"
    val infoUrl = new URL(urlStr)
    val res = Try(PluginDescriptor.load(infoUrl))
    res match {
      case Failure(exception) =>Left(exception)
      case Success(value) => Right(value)
    }
  }

  def getPluginDownloadURL(idea: BuildInfo, pluginInfo: IntellijPlugin.Id): URL = {
    pluginInfo match {
      case IntellijPlugin.Id(id, Some(version), Some(channel)) =>
        assert(pluginInfo.url.isEmpty, "Can't specify both channel and download URL.")
        new URL(s"$baseUrl/plugin/download?pluginId=$id&version=$version&channel=$channel")
      case IntellijPlugin.Id(id, Some(version), None) =>
        pluginInfo.url.getOrElse(new URL(s"$baseUrl/plugin/download?pluginId=$id&version=$version"))
      case IntellijPlugin.Id(id, None, Some(channel)) =>
        assert(pluginInfo.url.isEmpty, "Can't specify both channel and download URL.")
        val url = s"$baseUrl/pluginManager?action=download&noStatistic=true&id=$id&channel=$channel&build=${idea.edition.edition}-${idea.getActualIdeaBuild(ctx.baseDirectory)}"
        new URL(url)
      case IntellijPlugin.Id(id, None, None) =>
        val url = s"$baseUrl/pluginManager?action=download&noStatistic=true&&id=$id&build=${idea.edition.edition}-${idea.getActualIdeaBuild(ctx.baseDirectory)}"
        pluginInfo.url.getOrElse(new URL(url))
    }
  }

  def getLatestPluginVersion(idea: BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, String] =
    getRemotePluginXmlDescriptor(idea, pluginId, channel).right.map(_.version)
}
