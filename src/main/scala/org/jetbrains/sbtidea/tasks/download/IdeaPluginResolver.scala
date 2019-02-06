package org.jetbrains.sbtidea.tasks.download
import org.jetbrains.sbtidea.Keys.IdeaPlugin
import org.jetbrains.sbtidea.tasks.download.api.IdeaArtifactResolver
import sbt.URL

trait IdeaPluginResolver extends IdeaArtifactResolver {

  private val baseUrl = "https://plugins.jetbrains.com/pluginManager"

  override def resolvePlugin(idea: BuildInfo, pluginInfo: IdeaPlugin): ArtifactPart = {
    pluginInfo match {
      case IdeaPlugin.Zip(pluginName, pluginUrl) =>
        ArtifactPart(pluginUrl, ArtifactKind.PLUGIN_ZIP, pluginName)
      case IdeaPlugin.Jar(pluginName, pluginUrl) =>
        ArtifactPart(pluginUrl, ArtifactKind.PLUGIN_JAR, pluginName)
      case IdeaPlugin.Id(pluginName, id, channel) =>
        val chanStr = channel.map(c => s"&channel=$c").getOrElse("")
        val urlStr = s"$baseUrl?action=download&id=$id$chanStr&build=${idea.edition.shortname}-${idea.buildNumber}"
        ArtifactPart(new URL(urlStr), ArtifactKind.MISC, pluginName)
    }
  }

}
