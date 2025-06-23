package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.api.*

import java.net.URL
import scala.language.postfixOps

abstract class IdeaSources extends IdeaArtifact {
  override type R = IdeaSources
}

class IdeaSourcesImpl(
  override val caller: AbstractIdeaDependency,
  sourcesBuildInfo: BuildInfo
) extends IdeaSources {
  override def dlUrl: URL = IntellijRepositories.getArtifactUrl(sourcesBuildInfo, "-sources.jar")

  override protected def usedInstaller: Installer[IdeaSources] = new IdeaSourcesInstaller(sourcesBuildInfo)
}

object IdeaSourcesImpl {
  def apply(
    caller: AbstractIdeaDependency,
    sourcesBuildInfo: BuildInfo
  ): IdeaSourcesImpl = new IdeaSourcesImpl(caller, sourcesBuildInfo)
}