package org.jetbrains.sbtidea.download.jbr

import org.jetbrains.sbtidea.Keys.JBR
import org.jetbrains.sbtidea.download.api.Resolver
import org.jetbrains.sbtidea.{JbrPlatform, *}
import sbt.*

import java.net.URL
import java.nio.file.Path
import java.util.Properties
import scala.util.Using

class JbrResolver extends Resolver[JbrDependency] {
  import JbrResolver.*

  override def resolve(dep: JbrDependency): Seq[JbrArtifact] = {
    dep.jbrInfo match {
      case NoJbr => Seq.empty
      case jbrInfo =>
        val maybeArtifact =
          for {
            version <- getJbrVersion(dep)
          } yield {
            val kind = getJbrKind(jbrInfo, version)
            val platform = jbrInfo.platform
            val url = buildJbrDlUrl(version, kind, platform)
            val resolvedJbrInfo = JBR(version, kind, platform)
            JbrArtifact(dep.copy(jbrInfo = resolvedJbrInfo), url)
          }
        maybeArtifact.toSeq
    }
  }

  private def buildJbrDlUrl(jbrVersion: JbrVersion, jbrKind: JbrKind, jbrPlatform: JbrPlatform): URL = {
    val kind = jbrKind.value
    val JbrPlatform(platform, arch) = jbrPlatform
    val JbrVersion(major, minor) = jbrVersion
    new URL(s"$BASE_URL/$kind-$major-$platform-$arch-b$minor.tar.gz")
  }

  private def getJbrVersion(dep: JbrDependency): Option[JbrVersion] = dep.jbrInfo match {
    case AutoJbr(explicitVersion, _, _) =>
      explicitVersion.orElse(extractVersionFromIdea(dep.ideaRoot).map(JbrVersion.parse))
    case jbr =>
      Some(jbr.version)
  }

  private def getJbrKind(jbrInfo: JbrInfo, version: JbrVersion): JbrKind =
    jbrInfo match {
      case AutoJbr(_, explicitKind, _) =>
        explicitKind.getOrElse(defaultJbrFor(version))
      case jbr =>
        jbr.kind
    }

  private def defaultJbrFor(version: JbrVersion) =
    if (version.major.startsWith("11"))
      JbrKind.JBR_DCEVM
    else
      JbrKind.JBR_JCEF // jbr 17

  private[jbr] def extractVersionFromIdea(ideaInstallationDir: Path): Option[String] = {
    val dependenciesFile = ideaInstallationDir / "dependencies.txt"
    val props = new Properties()
    Using.resource(dependenciesFile.inputStream)(props.load)
    val value1 = Option(props.getProperty("runtimeBuild")) //since 2022.2
    val value2 = value1.orElse(Option(props.getProperty("jdkBuild")))
    value2
  }
}

object JbrResolver {
  val BASE_URL = "https://cache-redirector.jetbrains.com/intellij-jbr"
}
