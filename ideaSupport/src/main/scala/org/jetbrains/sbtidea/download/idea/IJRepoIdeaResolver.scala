package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.Keys
import org.jetbrains.sbtidea.download.api.Resolver

import scala.collection.mutable

class IJRepoIdeaResolver extends Resolver[IdeaDependency] {
  private val LoggerName = this.getClass.getSimpleName

  override def resolve(dep: IdeaDependency): Seq[IdeaArtifact] = {
    val ideaUrlGet = () => IntellijRepositories.getArtifactUrl(dep.buildInfo, ".zip")

    val result = mutable.Buffer[IdeaArtifact]()
    result += IdeaDistImpl(dep, ideaUrlGet)

    val platformCommunityBuildInfo = dep.buildInfo.withEdition(Keys.IntelliJPlatform.IdeaCommunity)
    result += IdeaSourcesImpl(dep, platformCommunityBuildInfo)
    if (dep.buildInfo.edition == Keys.IntelliJPlatform.IdeaUltimate) {
      // Sources for some ultimate plugins are published in ideaIU-sources.zip
      val platformUltimateBuildInfo = dep.buildInfo.withEdition(Keys.IntelliJPlatform.IdeaUltimate)
      result += IdeaSourcesImpl(dep, platformUltimateBuildInfo)
    }

    result
  }
}