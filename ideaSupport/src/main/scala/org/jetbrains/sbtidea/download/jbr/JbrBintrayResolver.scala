package org.jetbrains.sbtidea.download.jbr

import java.net.URL
import java.nio.file.Path
import java.util.{Locale, Properties}
import org.jetbrains.sbtidea.download.api.Resolver
import org.jetbrains.sbtidea.packaging.artifact.using
import org.jetbrains.sbtidea.Keys.{AutoJbr, AutoJbrWithKind, AutoJbrWithPlatform, JBR}
import org.jetbrains.sbtidea.download.plugin.RepoPluginInstaller.compareIdeaVersions
import org.jetbrains.sbtidea.{pathToPathExt, PluginLogger => log, _}
import sbt._

class JbrBintrayResolver extends Resolver[JbrDependency] {
  import JbrBintrayResolver._

  override def resolve(dep: JbrDependency): Seq[JbrArtifact] = {
    dep.jbrInfo match {
      case NoJbr => Seq.empty
      case jbrInfo =>
        val maybeArtifact =
          for {
            (major, minor)  <- getJbrVersion(dep)
            kind            <- getJbrKind(dep, minor)
            url             =  buildJbrDlUrl(major, minor, kind, dep)
            resolvedJbrInfo = JBR(major, minor, kind, jbrInfo.platform, jbrInfo.arch)
          } yield JbrArtifact(dep.copy(jbrInfo = resolvedJbrInfo), url)
        maybeArtifact.toSeq
    }
  }

  private[jbr] def buildJbrDlUrl(major: String, minor: String, kind: String, dep: JbrDependency): URL = {
    val info = dep.jbrInfo
    new URL(s"$BASE_URL/$kind-$major-${info.platform}-${info.arch}-b$minor.tar.gz")
  }

  private[jbr] def getJbrVersion(dep: JbrDependency): Option[(String, String)] = dep.jbrInfo match {
    case AutoJbr() =>
      extractVersionFromIdea(dep.ideaRoot).flatMap(splitVersion)
    case AutoJbrWithKind(_) =>
      extractVersionFromIdea(dep.ideaRoot).flatMap(splitVersion)
    case AutoJbrWithPlatform(major, minor, _) =>
      Some(major -> minor)
    case JBR(major, minor, _, _, _) =>
      Some(major -> minor)
  }

  private[jbr] def getJbrKind(dep: JbrDependency, minorVersion: String): Option[String] = dep.jbrInfo match {
    case JBR(_, _, kind, _, _) =>
      Some(kind)
    case AutoJbrWithKind(kind) =>
      Some(kind)
    case AutoJbrWithPlatform(_, _, kind) =>
      Some(kind)
    case AutoJbr() =>
      if (compareIdeaVersions(minorVersion, "1145.77") >= 0)
        Some(JBR_DCEVM_KIND)
      else
        Some(JBR_DEFAULT_KIND)
  }

  private[jbr] def extractVersionFromIdea(ideaInstallationDir: Path): Option[String] = {
    val dependenciesFile = ideaInstallationDir / "dependencies.txt"
    val props = new Properties()
    using(dependenciesFile.inputStream)(props.load)
    props.getProperty("jdkBuild").lift2Option
  }

}

object JbrBintrayResolver {
  val BASE_URL        = "https://cache-redirector.jetbrains.com/jetbrains.bintray.com/intellij-jbr"

  val JBR_DEFAULT_KIND      = "jbr"
  val JBR_DCEVM_KIND        = "jbr_dcevm"
  val JBR_JCEF_KIND         = "jbr_jcef"

  def splitVersion(version: String): Option[(String, String)] = {
    val lastIndexOfB = version.lastIndexOf('b')
    if (lastIndexOfB > -1)
      Some(version.substring(0, lastIndexOfB) -> version.substring(lastIndexOfB + 1))
    else {
      throw new IllegalStateException(s"Malformed jbr version: $version")
    }
  }
}
