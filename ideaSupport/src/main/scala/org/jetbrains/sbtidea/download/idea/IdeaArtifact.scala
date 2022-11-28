package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.download.api.*

trait IdeaArtifact extends ResolvedArtifact with UrlBasedArtifact {
  def caller: AbstractIdeaDependency
}


