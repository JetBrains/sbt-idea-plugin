package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.Keys.IntellijPlugin
import org.jetbrains.sbtidea.download.api._

trait JBPluginRepoResolver extends PluginResolver {

  override def resolvePlugin(idea: BuildInfo, pluginInfo: IntellijPlugin): ArtifactPart = {
    pluginInfo match {
      case IntellijPlugin.Url(url) =>
        ArtifactPart(url, ArtifactKind.IDEA_PLUGIN)
      case plugin:IntellijPlugin.Id =>
        ArtifactPart(PluginRepoUtils.getPluginDownloadURL(idea, plugin), ArtifactKind.IDEA_PLUGIN)
    }
  }

}
