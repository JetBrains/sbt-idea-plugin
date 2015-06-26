package com.dancingrobot84.sbtidea

import sbt._
import sbt.Keys._
import scala.util._
import scala.xml._


object Tasks {
  import Downloader._
  import Keys._

  def updateIdea(baseDir: File, build: String, externalPlugins: Seq[IdeaPlugin], streams: TaskStreams): Unit = {
    var downloads = Seq.empty[Download]

    if (baseDir.isDirectory) {
      streams.log.info(s"Skip downloading and unpacking IDEA because $baseDir exists")
    } else {
      IO.createDirectory(baseDir)
      downloads = downloads ++ createIdeaDownloads(baseDir, build, streams.log)
    }

    val externalPluginsDir = baseDir / "externalPlugins"
    IO.createDirectory(externalPluginsDir)
    downloads = downloads ++ createExternalPluginsDownloads(externalPluginsDir, externalPlugins, streams.log)

    downloads.foreach(d => download(d, streams.log))
    movePluginsIntoRightPlace(externalPluginsDir, externalPlugins)
  }

  def createPluginsClasspath(pluginsBase: File, pluginsUsed: Seq[String]): Classpath = {
    val pluginsDirs = pluginsUsed.foldLeft(PathFinder.empty) { (paths, plugin) =>
      paths +++ (pluginsBase / plugin) +++ (pluginsBase / plugin / "lib")
    }
    (pluginsDirs * (globFilter("*.jar") -- "asm*.jar")).classpath
  }

  private def createIdeaDownloads(baseDir: File, build: String, log: Logger): Seq[Download] = {
    val repositoryUrl = getRepositoryForBuild(build)
    val ideaUrl = s"$repositoryUrl/ideaIC/$build/ideaIC-$build.zip"
    val ideaSourcesUrl = s"$repositoryUrl/ideaIC/$build/ideaIC-$build-sources.zip"
    Seq(
      DownloadAndUnpack(
        url(ideaUrl),
        baseDir.getParentFile / s"ideaIC-$build.zip",
        baseDir),
      DownloadOnly(
        url(ideaSourcesUrl),
        baseDir / "sources.zip")
    )
  }

  private def getRepositoryForBuild(build: String): String = {
    val repository = if (build.endsWith("SNAPSHOT")) "snapshots" else "releases"
    s"https://www.jetbrains.com/intellij-repository/$repository/com/jetbrains/intellij/idea"
  }

  private def createExternalPluginsDownloads(baseDir: File, plugins: Seq[IdeaPlugin], log: Logger): Seq[Download] =
    plugins.flatMap { plugin =>
      val pluginDir = baseDir / plugin.name
      if (pluginDir.isDirectory) {
        log.info(s"Skip downloading ${plugin.name} external plugin because $pluginDir exists")
        None
      } else {
        Some(plugin match {
          case IdeaPlugin.Zip(pluginName, pluginUrl) =>
            DownloadAndUnpack(
              pluginUrl,
              baseDir / s"$pluginName.zip",
              baseDir / pluginName
            )
          case IdeaPlugin.Jar(pluginName, pluginUrl) =>
            DownloadOnly(
              pluginUrl,
              baseDir / s"$pluginName.jar"
            )
        })
      }
    }

  private def movePluginsIntoRightPlace(externalPluginsDir: File, plugins: Seq[IdeaPlugin]): Unit =
    plugins.foreach { plugin =>
      val pluginDir = externalPluginsDir / plugin.name
      if (pluginDir.isDirectory) {
        pluginDir.listFiles match {
          case Array(dir) if dir.isDirectory && dir.getName != "lib" =>
            IO.copyDirectory(dir, pluginDir)
            IO.delete(dir)
          case _ => // ignore
        }
      }
    }
}

private object Downloader {
  sealed trait Download
  final case class DownloadOnly(from: URL, to: File) extends Download
  final case class DownloadAndUnpack(from: URL, to: File, unpackTo: File) extends Download

  def download(d: Download, log: Logger): Unit = d match {
    case DownloadOnly(from, to) =>
      download(log, from, to)
    case DownloadAndUnpack(from, to, unpackTo) =>
      download(log, from, to)
      unpack(log, to, unpackTo)
  }

  private def download(log: Logger, from: URL, to: File): Unit = {
    if (to.isFile) {
      log.info(s"Skip downloading $from because $to exists")
    } else {
      log.info(s"Downloading $from to $to")
      IO.download(from, to)
    }
  }

  private def unpack(log: Logger, from: File, to: File): Unit = {
    log.info(s"Unpacking $from to $to")
    IO.unzip(from, to)
  }
}
