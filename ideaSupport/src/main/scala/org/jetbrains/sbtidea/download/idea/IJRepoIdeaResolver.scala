package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.{Keys, PluginLogger as log}
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.IdeaUpdater.IJ_REPO_OVERRIDE
import org.jetbrains.sbtidea.download.api.Resolver
import sbt.{URL, url}

import java.io.{FileNotFoundException, InputStream}

class IJRepoIdeaResolver extends Resolver[IdeaDependency] {

  override def resolve(dep: IdeaDependency): Seq[IdeaArtifact] = {
    val ideaUrl           = () => getUrl(dep.buildInfo, ".zip")
    // sources are available only for Community Edition
    val srcJarUrl         = () => getUrl(dep.buildInfo.copy(edition = Keys.IntelliJPlatform.IdeaCommunity), "-sources.jar")
    IdeaDistImpl(dep, ideaUrl) ::
    IdeaSourcesImpl(dep, srcJarUrl) :: Nil
  }

  private val defaultBaseURL    = "https://www.jetbrains.com/intellij-repository"

  private def getCoordinates(platform: IntelliJPlatform): (String, String) = platform match {
    case IntelliJPlatform.IdeaCommunity       => "com/jetbrains/intellij/idea" -> "ideaIC"
    case IntelliJPlatform.IdeaUltimate        => "com/jetbrains/intellij/idea" -> "ideaIU"
    case IntelliJPlatform.PyCharmCommunity    => "com/jetbrains/intellij/pycharm" -> "pycharmPC"
    case IntelliJPlatform.PyCharmProfessional => "com/jetbrains/intellij/pycharm" -> "pycharmPY"
    case IntelliJPlatform.CLion               => "com/jetbrains/intellij/clion" -> "clion"
    case IntelliJPlatform.MPS                 => "com/jetbrains/mps" -> "mps"
  }

  //noinspection NoTailRecursionAnnotation
  protected def getUrl(platform: BuildInfo, artifactSuffix: String, trySnapshot: Boolean = false): URL = {
    val (repo, buildNumberSuffix)  =
      if      (platform.buildNumber.count(_ == '.') == 1) "nightly"   -> "-SNAPSHOT"
      else if (trySnapshot)                               "snapshots" -> "-EAP-SNAPSHOT"
      else if (platform.buildNumber.contains("SNAPSHOT")) "snapshots" -> ""
      else                                                "releases"  -> ""
    val (groupId, artifactId) = getCoordinates(platform.edition)
    val urlFormEnv  = System.getProperty(IJ_REPO_OVERRIDE)
    val baseURL     = if (urlFormEnv != null) {
      log.warn(s"Using non-default IntelliJ repository URL: $urlFormEnv")
      urlFormEnv
    } else defaultBaseURL
    val repoURL         = s"$baseURL/$repo/$groupId"
    def withoutTail(version: String) = version.substring(0, version.lastIndexOf('.'))
    val build           = (if (repo == "nightly" ) withoutTail(platform.buildNumber) else platform.buildNumber) + buildNumberSuffix
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