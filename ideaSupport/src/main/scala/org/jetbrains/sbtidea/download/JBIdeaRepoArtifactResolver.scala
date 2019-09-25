package org.jetbrains.sbtidea.download

import java.io.{FileNotFoundException, InputStream}

import org.jetbrains.sbtidea.Keys
import org.jetbrains.sbtidea.download.api.IdeaResolver
import sbt.{URL, url}

trait JBIdeaRepoArtifactResolver extends IdeaResolver {

  override def resolveUrlForIdeaBuild(idea: BuildInfo): Seq[ArtifactPart] = {
    val (build, edition)  = (idea.buildNumber, idea.edition.name)
    val ideaUrl           = getUrl(idea, ".zip")
    // sources are available only for Community Edition
    val srcJarUrl         = getUrl(idea.copy(edition = Keys.IdeaEdition.Community), "-sources.jar")

    ArtifactPart(ideaUrl, ArtifactKind.IDEA_DIST, s"$edition-$build.zip") ::
      ArtifactPart(srcJarUrl, ArtifactKind.IDEA_SRC, s"$edition-$build-sources.jar", optional = true) :: Nil
  }

  //noinspection NoTailRecursionAnnotation
  protected def getUrl(idea: BuildInfo, artifactSuffix: String, trySnapshot: Boolean = false): URL = {
    val (repo, suffix)  =
      if      (trySnapshot)                           "snapshots" -> "-EAP-SNAPSHOT"
      else if (idea.buildNumber.contains("SNAPSHOT")) "snapshots" -> ""
      else                                            "releases"  -> ""
    val baseUrl         = s"https://www.jetbrains.com/intellij-repository/$repo/com/jetbrains/intellij/idea"
    val build           = idea.buildNumber + suffix
    var stream: Option[InputStream] = None
    try {
      val result  = url(s"$baseUrl/${idea.edition.name}/$build/${idea.edition.name}-$build$artifactSuffix")
      stream      = Some(result.openStream())
      result
    } catch {
      case _: FileNotFoundException if !trySnapshot && !idea.buildNumber.endsWith("SNAPSHOT") =>
        println(s"Can't find $idea in releases, trying snapshots")
        getUrl(idea, artifactSuffix, trySnapshot = true)
    } finally {
      stream.foreach(_.close())
    }
  }

}
