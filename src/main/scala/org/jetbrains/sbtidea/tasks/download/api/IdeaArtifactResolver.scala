package org.jetbrains.sbtidea.tasks.download.api

import org.jetbrains.sbtidea.Keys.IdeaPlugin
import org.jetbrains.sbtidea.tasks.download.{ArtifactPart, BuildInfo}

trait IdeaArtifactResolver {
  /**
    * Discovers all necessary files to be downloaded for a particular IDEA version.
    * This may include source archives and additional jars.
    */
  def resolveUrlForIdeaBuild(build: BuildInfo): Seq[ArtifactPart]

  /**
    * Plugins seem to only resolve to a single file: an archive or a jar
    */
  def resolvePlugin(buildInfo: BuildInfo, pluginInfo: IdeaPlugin): ArtifactPart
}
