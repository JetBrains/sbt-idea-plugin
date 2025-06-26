package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.Keys
import org.jetbrains.sbtidea.download.api.Resolver

import scala.collection.mutable

class IJRepoIdeaResolver extends Resolver[IdeaDependency] {
  override def resolve(dep: IdeaDependency): Seq[IdeaArtifact] = {
    val ideaUrlGet = () => IntellijRepositories.getArtifactUrl(dep.buildInfo, ".zip")

    val result = mutable.Buffer[IdeaArtifact]()
    result += IdeaDistImpl(dep, ideaUrlGet)

    val sources = if (dep.buildInfo.edition == Keys.IntelliJPlatform.IdeaUltimate) {
      // Ultimate sources already include all community sources
      val platformUltimateBuildInfo = dep.buildInfo.withEdition(Keys.IntelliJPlatform.IdeaUltimate)
      IdeaSourcesImpl(dep, platformUltimateBuildInfo)
    } else {
      val platformCommunityBuildInfo = dep.buildInfo.withEdition(Keys.IntelliJPlatform.IdeaCommunity)
      IdeaSourcesImpl(dep, platformCommunityBuildInfo)
    }

    result += sources

    result
  }
}