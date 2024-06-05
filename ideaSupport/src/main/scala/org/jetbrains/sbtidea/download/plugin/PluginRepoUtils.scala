package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.IntellijPlugin
import org.jetbrains.sbtidea.download.*
import org.jetbrains.sbtidea.download.api.InstallContext
import sbt.URL

import scala.util.Try

class PluginRepoUtils(implicit ctx: InstallContext) extends PluginRepoApi {

  override def getRemotePluginXmlDescriptor(idea: BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, PluginDescriptor] = {
    val url = MerketplaceUrls.pluginsList(pluginId, idea, channel)
    val result = Try(PluginDescriptor.load(url))
    result.toEither
  }

  override def getPluginDownloadURL(idea: BuildInfo, pluginInfo: IntellijPlugin.Id): URL =
    pluginInfo match {
      case IntellijPlugin.Id(id, Some(version), channel, _) =>
        MerketplaceUrls.download(id, version, channel)
      case IntellijPlugin.Id(id, None, channel, _) =>
        MerketplaceUrls.downloadViaPluginManager(id, idea, channel)
    }

  private object MerketplaceUrls {
    private val BaseUrl = "https://plugins.jetbrains.com"

    def pluginsList(id: String, buildInfo: BuildInfo, channel: Option[String]): URL = {
      val edition = buildInfo.edition.edition
      val buildNumber = ctx.productInfo.buildNumber
      val channelQuery = channel.fold("")(c => s"&channel=$c")
      new URL(s"$BaseUrl/plugins/list?pluginId=$id$channelQuery&build=$edition-$buildNumber")
    }

    def download(id: String, version: String, channel: Option[String]): URL = {
      val channelQuery = channel.fold("")(c => s"&channel=$c")
      new URL(s"$BaseUrl/plugin/download?noStatistic=true&pluginId=$id&version=$version$channelQuery")
    }

    def downloadViaPluginManager(id: String, buildInfo: BuildInfo, channel: Option[String]): URL = {
      val edition = buildInfo.edition.edition
      val buildNumber = ctx.productInfo.buildNumber
      val channelQuery = channel.fold("")(c => s"&channel=$c")
      new URL(s"$BaseUrl/pluginManager?action=download&noStatistic=true&id=$id$channelQuery&build=$edition-$buildNumber")
    }
  }

  def getLatestPluginVersion(idea: BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, String] =
    getRemotePluginXmlDescriptor(idea, pluginId, channel).right.map(_.version)
}
