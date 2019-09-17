package org.jetbrains.sbtidea.download.api

import org.jetbrains.sbtidea.LogAware
import org.jetbrains.sbtidea.download.{ArtifactPart, BuildInfo}

trait IdeaResolver extends LogAware {
  /**
    * Discovers all necessary files to be downloaded for a particular IDEA version.
    * This may include source archives and additional jars.
    */
  def resolveUrlForIdeaBuild(build: BuildInfo): Seq[ArtifactPart]
}
