package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.Keys.IdeaPlugin
import org.jetbrains.sbtidea.download.api._

trait JBPluginRepoResolver extends PluginResolver {

  private val baseUrl = "https://plugins.jetbrains.com/pluginManager"

  override def resolvePlugin(idea: BuildInfo, pluginInfo: IdeaPlugin): ArtifactPart = {
    pluginInfo match {
      case IdeaPlugin.Url(url) =>
        ArtifactPart(url, ArtifactKind.IDEA_PLUGIN)
      case plugin:IdeaPlugin.Id =>
        ArtifactPart(PluginRepoUtils.getPluginDownloadURL(idea, plugin), ArtifactKind.MISC)
    }
  }

}
