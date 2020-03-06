package org.jetbrains.sbtidea.download.jbr

import java.net.URL
import java.nio.file.Path

import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.api._

case class JbrDependency(ideaRoot: Path, buildInfo: BuildInfo, dependsOn: Seq[UnresolvedArtifact] = Seq.empty) extends UnresolvedArtifact {
  override type U = JbrDependency
  override type R = JbrArtifact
  override protected def usedResolver: JbrBintrayResolver = new JbrBintrayResolver
}

object JbrDependency {
  val VERSION_AUTO    = "__auto__"
}

case class JbrArtifact(caller: JbrDependency, dlUrl: URL) extends ResolvedArtifact with UrlBasedArtifact {
  override type R = JbrArtifact
  override protected def usedInstaller: JbrInstaller = new JbrInstaller
}