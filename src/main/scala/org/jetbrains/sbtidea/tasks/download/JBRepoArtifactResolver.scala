package org.jetbrains.sbtidea.tasks.download

import java.io.{FileNotFoundException, InputStream}

import org.jetbrains.sbtidea.tasks.download.api.IdeaArtifactResolver
import sbt.{URL, url}

class JBRepoArtifactResolver extends IdeaArtifactResolver with IdeaPluginResolver {

  override def resolveUrlForIdeaBuild(idea: BuildInfo): Seq[ArtifactPart] = {
    val (build, edition)  = (idea.buildNumber, idea.edition.name)
    val ideaUrl           = getUrl(idea) { build => s"$edition-$build.zip" }
    val srcJarUrl         = getUrl(idea) { build => s"$edition-$build-sources.jar" }

    ArtifactPart(ideaUrl, ArtifactKind.IDEA_DIST, s"$edition-$build.zip") ::
      ArtifactPart(srcJarUrl, ArtifactKind.IDEA_SRC, s"$edition-$build-sources.jar", optional = true) :: Nil
  }

  //noinspection NoTailRecursionAnnotation
  protected def getUrl(idea: BuildInfo, trySnapshot: Boolean = false)(artName: String => String): URL = {
    val (repo, suffix)  =
      if      (trySnapshot)                           "snapshots" -> "-EAP-SNAPSHOT"
      else if (idea.buildNumber.contains("SNAPSHOT")) "snapshots" -> ""
      else                                            "releases"  -> ""
    val baseUrl         = s"https://www.jetbrains.com/intellij-repository/$repo/com/jetbrains/intellij/idea"
    val build           = idea.buildNumber + suffix
    var stream: Option[InputStream] = None
    try {
      val result  = url(s"$baseUrl/${idea.edition.name}/$build/${artName(build)}")
      stream      = Some(result.openStream())
      result
    } catch {
      case _: FileNotFoundException if !trySnapshot && !idea.buildNumber.endsWith("SNAPSHOT") =>
        println(s"Can't find $idea in releases, trying snapshots")
        getUrl(idea, trySnapshot = true)(artName)
    } finally {
      stream.foreach(_.close())
    }
  }

}
