package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.Keys
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.api.Resolver

import scala.annotation.nowarn
import scala.collection.mutable

class IJRepoIdeaResolver extends Resolver[IdeaDependency] {
  override def resolve(dep: IdeaDependency): Seq[IdeaArtifact] = {
    val ideaUrlGet = () => IntellijRepositories.getArtifactUrl(dep.buildInfo, ".zip")

    val result = mutable.Buffer[IdeaArtifact]()
    result += IdeaDistImpl(dep, ideaUrlGet)

    val sources = IdeaSourcesImpl(dep, sourcesBuildInfo(dep.buildInfo))

    result += sources

    result
  }

  @nowarn("cat=deprecation")
  private def sourcesBuildInfo(buildInfo: BuildInfo): BuildInfo = {
    val sourcesEdition =
      if (buildInfo.edition.name == Keys.IntelliJPlatform.Idea.name)
        Keys.IntelliJPlatform.Idea
      else
        Keys.IntelliJPlatform.IdeaCommunity

    buildInfo.withEdition(sourcesEdition)
  }
}
