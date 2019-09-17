package org.jetbrains.sbtidea.download.api

import org.jetbrains.sbtidea.download.{ArtifactPart, BuildInfo}
import org.jetbrains.sbtidea.Keys.IdeaPlugin
import org.jetbrains.sbtidea.LogAware

trait PluginResolver extends LogAware {
  /**
    * Plugins seem to only resolve to a single file: an archive or a jar
    */
  def resolvePlugin(buildInfo: BuildInfo, pluginInfo: IdeaPlugin): ArtifactPart
}
