package org.jetbrains.sbtidea.download

import java.net.URL
import java.nio.file.{Files, Path}
import java.util.{Locale, Properties}

import org.jetbrains.sbtidea.packaging.artifact.using
import org.jetbrains.sbtidea.{PluginLogger, _}
import org.rauschig.jarchivelib.{ArchiveFormat, ArchiverFactory, CompressionType}
import org.jetbrains.sbtidea.pathToPathExt

import sbt._

class JbrInstaller(baseDir: Path, bundledVersionProvider: => Option[String])(implicit log: PluginLogger) {
  import JbrInstaller._

  def downloadAndInstall(buildInfo: BuildInfo): Unit = {
    if (!isAlreadyInstalled) {
      val version = buildInfo.jbrVersion match {
        case Some(VERSION_AUTO)   => bundledVersionProvider
        case otherVersion@Some(_) => otherVersion
        case None => None
      }
      version
        .flatMap(download)
        .foreach(install)
    }
  }

  private def download(version: String): Option[Path] = {
    val (major, minor) = splitVersion(version)
    val url = s"$BASE_URL/jbr-$major-$platform-$arch-b$minor.tar.gz"
    val part = ArtifactPart(new URL(url), ArtifactKind.MISC)
    val downloader = new FileDownloader(baseDir.getParent, log)
    try {
      downloader.download(part).lift2Option
    } catch {
      case e: Exception =>
        log.error(s"Failed to download jbr $version: $e")
        None
    }
  }

  private def install(dist: Path): Unit = {
    val archiver = ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP)
    val tmpDir = Files.createTempDirectory(baseDir, "jbr-extract")
    log.info(s"extracting jbr to $tmpDir")
    archiver.extract(dist.toFile, tmpDir.toFile)
    val installPath = baseDir / JBR_DIR_NAME
    val children = tmpDir.list
    if (children.size == 1) {
      NioUtils.delete(installPath)
      Files.move(children.head, installPath)
      NioUtils.delete(tmpDir)
      log.info(s"installed JBR into $installPath")
    } else {
      log.error(s"Unexpected JBR archive structure, expected single directory")
    }
  }

  private def isAlreadyInstalled: Boolean = (baseDir / JBR_DIR_NAME).exists

  private def splitVersion(version: String): (String, String) = {
    val lastIndexOfB = version.lastIndexOf('b')
    if (lastIndexOfB > -1)
      version.substring(0, lastIndexOfB) -> version.substring(lastIndexOfB + 1)
    else {
      log.error(s"Malformed jbr version: $version")
      "" -> ""
    }
  }

  private def prefix: String = "jbrsdk11"

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
}

object JbrInstaller {
  val BASE_URL        = "https://cache-redirector.jetbrains.com/jetbrains.bintray.com/intellij-jbr"
  val VERSION_AUTO    = "__auto__"
  val JBR_DIR_NAME    = "jbr"

  def extractVersionFromIdea(ideaInstallationDir: Path): Option[String] = {
    val dependenciesFile = ideaInstallationDir / "dependencies.txt"
    val props = new Properties()
    using(dependenciesFile.inputStream)(props.load)
    props.getProperty("jdkBuild").lift2Option
  }
}