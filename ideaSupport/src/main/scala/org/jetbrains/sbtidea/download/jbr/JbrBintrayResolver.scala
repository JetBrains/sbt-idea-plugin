package org.jetbrains.sbtidea.download.jbr

import java.net.URL
import java.nio.file.Path
import java.util.{Locale, Properties}

import org.jetbrains.sbtidea.download.api.Resolver
import org.jetbrains.sbtidea.download.jbr.JbrDependency.VERSION_AUTO
import org.jetbrains.sbtidea.packaging.artifact.using
import org.jetbrains.sbtidea.{pathToPathExt, PluginLogger => log, _}
import sbt._

class JbrBintrayResolver extends Resolver[JbrDependency] {
  import JbrBintrayResolver._

  override def resolve(dep: JbrDependency): Seq[JbrArtifact] = {
    getJbrVersion(dep)
      .flatMap(buildJbrDlUrl)
      .map(url => JbrArtifact(dep, url))
      .toSeq
  }

  private[jbr] def buildJbrDlUrl(version: String): Option[URL] = splitVersion(version).flatMap {
    case (major, minor) => Some(new URL(s"$BASE_URL/jbr-$major-$platform-$arch-b$minor.tar.gz"))
    case _ =>
      log.error(s"Unexpected version format from $version")
      None
  }

  private[jbr] def getJbrVersion(dep: JbrDependency): Option[String] = dep.buildInfo.jbrVersion match {
    case Some(VERSION_AUTO)   => extractVersionFromIdea(dep.ideaRoot)
    case otherVersion@Some(_) => otherVersion
    case None => None
  }


  private def platform: String = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH) match {
    case value if value.startsWith("win") => "windows"
    case value if value.startsWith("lin") => "linux"
    case value if value.startsWith("mac") => "osx"
    case other => log.error(s"Unsupported jbr os: $other"); ""
  }

  private def arch: String = System.getProperty("os.arch") match {
    case "amd64"  => "x64"
    case other    => other
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

  def splitVersion(version: String): Option[(String, String)] = {
    val lastIndexOfB = version.lastIndexOf('b')
    if (lastIndexOfB > -1)
      Some(version.substring(0, lastIndexOfB) -> version.substring(lastIndexOfB + 1))
    else {
      log.error(s"Malformed jbr version: $version")
      None
    }
  }
}
