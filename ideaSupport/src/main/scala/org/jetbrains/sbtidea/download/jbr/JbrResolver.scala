package org.jetbrains.sbtidea.download.jbr

import org.jetbrains.sbtidea.Keys.JBR
import org.jetbrains.sbtidea.download.api.Resolver
import org.jetbrains.sbtidea.packaging.artifact.using
import org.jetbrains.sbtidea.{JbrPlatform, pathToPathExt, _}
import sbt._

import java.net.URL
import java.nio.file.Path
import java.util.Properties

class JbrResolver extends Resolver[JbrDependency] {
  import JbrResolver._

  override def resolve(dep: JbrDependency): Seq[JbrArtifact] = {
    dep.jbrInfo match {
      case NoJbr => Seq.empty
      case jbrInfo =>
        val maybeArtifact =
          for {
            version <- getJbrVersion(dep)
          } yield {
            val url = buildJbrDlUrl(version, jbrInfo.kind, jbrInfo.platform)
            val resolvedJbrInfo = JBR(version, jbrInfo.kind, jbrInfo.platform)
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

  private[jbr] def extractVersionFromIdea(ideaInstallationDir: Path): Option[String] = {
    val dependenciesFile = ideaInstallationDir / "dependencies.txt"
    val props = new Properties()
    using(dependenciesFile.inputStream)(props.load)
    props.getProperty("jdkBuild").lift2Option
  }
}

object JbrResolver {
  val BASE_URL = "https://cache-redirector.jetbrains.com/intellij-jbr"
}
