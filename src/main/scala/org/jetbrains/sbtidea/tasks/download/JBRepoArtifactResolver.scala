package org.jetbrains.sbtidea.tasks.download

import java.net.URL

import org.jetbrains.sbtidea.tasks.download.api.IdeaArtifactResolver

class JBRepoArtifactResolver extends IdeaArtifactResolver with IdeaPluginResolver {

  override def resolveUrlForIdeaBuild(idea: BuildInfo): Seq[ArtifactPart] = {
    val (build, edition)  = (idea.buildNumber, idea.edition.name)
    val repositoryUrl     = getRepositoryForBuild(idea)
    val ideaUrl           = new URL(s"$repositoryUrl/$edition/$build/$edition-$build.zip")
    val srcJarUrl         = new URL(s"$repositoryUrl/$edition/$build/$edition-$build-sources.jar")

    ArtifactPart(ideaUrl, ArtifactKind.IDEA_DIST, s"$edition-$build.zip") ::
      ArtifactPart(srcJarUrl, ArtifactKind.IDEA_SRC, s"$edition-$build-sources.jar", optional = true) :: Nil
  }

  private def getRepositoryForBuild(idea: BuildInfo): String = {
    val repository = if (idea.buildNumber.endsWith("SNAPSHOT")) "snapshots" else "releases"
    s"https://www.jetbrains.com/intellij-repository/$repository/com/jetbrains/intellij/idea"
  }

}
