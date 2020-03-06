package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.api.{InstallContext, UnresolvedArtifact}

case class IdeaDependency(buildInfo: BuildInfo) extends UnresolvedArtifact {
  override type U = IdeaDependency
  override type R = IdeaArtifact
  override protected def usedResolver: IJRepoIdeaResolver = new IJRepoIdeaResolver
  override def dependsOn: Seq[UnresolvedArtifact] = Seq.empty
}