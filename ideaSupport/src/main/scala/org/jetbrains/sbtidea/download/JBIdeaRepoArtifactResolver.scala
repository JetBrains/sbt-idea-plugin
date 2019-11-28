package org.jetbrains.sbtidea.download

import java.io.{FileNotFoundException, InputStream}

import org.jetbrains.sbtidea.Keys
import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.download.api.IdeaResolver
import sbt.{URL, url}

trait JBIdeaRepoArtifactResolver extends IdeaResolver {

  private val IJ_REPO_OVERRIDE  = "sbtidea.ijrepo"
  private val defaultBaseURL    = "https://www.jetbrains.com/intellij-repository"

  private def getCoordinates(platform: IntelliJPlatform): (String, String) = platform match {
    case IntelliJPlatform.IdeaCommunity       => "com/jetbrains/intellij/idea" -> "ideaIC"
    case IntelliJPlatform.IdeaUltimate        => "com/jetbrains/intellij/idea" -> "ideaIU"
    case IntelliJPlatform.PyCharmCommunity    => "com/jetbrains/intellij/pycharm" -> "pycharmPC"
    case IntelliJPlatform.PyCharmProfessional => "com/jetbrains/intellij/pycharm" -> "pycharmPY"
    case IntelliJPlatform.CLion               => "com/jetbrains/intellij/clion" -> "clion"
    case IntelliJPlatform.MPS                 => "com/jetbrains/mps" -> "mps"
  }

  override def resolveUrlForIdeaBuild(idea: BuildInfo): Seq[ArtifactPart] = {
    val (build, edition)  = (idea.buildNumber, idea.edition.name)
    val ideaUrl           = getUrl(idea, ".zip")
    // sources are available only for Community Edition
    val srcJarUrl         = getUrl(idea.copy(edition = Keys.IntelliJPlatform.IdeaCommunity), "-sources.jar")

    ArtifactPart(ideaUrl, ArtifactKind.IDEA_DIST, s"$edition-$build.zip") ::
      ArtifactPart(srcJarUrl, ArtifactKind.IDEA_SRC, s"$edition-$build-sources.jar", optional = true) :: Nil
  }

  //noinspection NoTailRecursionAnnotation
  protected def getUrl(platform: BuildInfo, artifactSuffix: String, trySnapshot: Boolean = false): URL = {
    val (repo, buildNumberSuffix)  =
      if      (trySnapshot)                               "snapshots" -> "-EAP-SNAPSHOT"
      else if (platform.buildNumber.contains("SNAPSHOT")) "snapshots" -> ""
      else                                                "releases"  -> ""
    val (groupId, artifactId) = getCoordinates(platform.edition)
    val urlFormEnv  = System.getProperty(IJ_REPO_OVERRIDE)
    val baseURL     = if (urlFormEnv != null) {
      log.warn(s"Using non-default IntelliJ repository URL: $urlFormEnv")
      urlFormEnv
    } else defaultBaseURL
    val repoURL         = s"$baseURL/$repo/$groupId"
    val build           = platform.buildNumber + buildNumberSuffix
    var stream: Option[InputStream] = None
    try {
      val result  = url(s"$repoURL/$artifactId/$build/$artifactId-$build$artifactSuffix")
      stream      = Some(result.openStream())
      result
    } catch {
      case _: FileNotFoundException if !trySnapshot && !platform.buildNumber.endsWith("SNAPSHOT") =>
        println(s"Can't find $platform in releases, trying snapshots")
        getUrl(platform, artifactSuffix, trySnapshot = true)
    } finally {
      stream.foreach(_.close())
    }
  }

}
