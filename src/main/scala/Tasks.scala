package com.dancingrobot84.sbtidea

import sbt._
import sbt.Keys._
import scala.util._
import scala.xml._
import java.io.IOException


object Tasks {
  import Keys._

  def updateIdea(baseDir: File, edition: IdeaEdition, build: String, externalPlugins: Seq[IdeaPlugin], streams: TaskStreams): Unit = {
    implicit val log = streams.log

    if (baseDir.isDirectory) {
      log.info(s"Skip downloading and unpacking IDEA because $baseDir exists")
    } else {
      IO.createDirectory(baseDir)
      downloadIdeaAndSources(baseDir, edition, build)
    }

    val externalPluginsDir = baseDir / "externalPlugins"
    IO.createDirectory(externalPluginsDir)
    downloadExternalPlugins(externalPluginsDir, externalPlugins)
    movePluginsIntoRightPlace(externalPluginsDir, externalPlugins)
  }

  def createPluginsClasspath(pluginsBase: File, pluginsUsed: Seq[String]): Classpath = {
    val pluginsDirs = pluginsUsed.foldLeft(PathFinder.empty) { (paths, plugin) =>
      paths +++ (pluginsBase / plugin) +++ (pluginsBase / plugin / "lib")
    }
    (pluginsDirs * (globFilter("*.jar") -- "asm*.jar")).classpath
  }

  private def downloadIdeaAndSources(baseDir: File, edition: IdeaEdition, build: String)(implicit log: Logger): Unit = {
    val repositoryUrl = getRepositoryForBuild(build)

    val ideaUrl = url(s"$repositoryUrl/${edition.name}/$build/${edition.name}-$build.zip")
    val ideaZipFile = baseDir.getParentFile / s"${edition.name}-$build.zip"
    downloadOrFail(ideaUrl, ideaZipFile)
    unpack(ideaZipFile, baseDir)

    val ideaSourcesJarUrl = url(s"$repositoryUrl/ideaIC/$build/ideaIC-$build-sources.jar")
    val ideaSourcesZipUrl = url(s"$repositoryUrl/ideaIC/$build/ideaIC-$build-sources.zip")
    val ideaSourcesZipFile = baseDir / "sources.zip"
    downloadOrLog(ideaSourcesJarUrl, ideaSourcesZipFile)
    downloadOrLog(ideaSourcesZipUrl, ideaSourcesZipFile)
  }

  private def getRepositoryForBuild(build: String): String = {
    val repository = if (build.endsWith("SNAPSHOT")) "snapshots" else "releases"
    s"https://www.jetbrains.com/intellij-repository/$repository/com/jetbrains/intellij/idea"
  }

  private def downloadExternalPlugins(baseDir: File, plugins: Seq[IdeaPlugin])(implicit log: Logger): Unit =
    plugins.foreach { plugin =>
      val pluginDir = baseDir / plugin.name
      if (pluginDir.isDirectory)
        log.info(s"Skip downloading ${plugin.name} external plugin because $pluginDir exists")
      else
        plugin match {
          case IdeaPlugin.Zip(pluginName, pluginUrl) =>
            val pluginZipFile = baseDir / s"$pluginName.zip"
            downloadOrFail(pluginUrl, pluginZipFile)
            unpack(pluginZipFile, baseDir / pluginName)
          case IdeaPlugin.Jar(pluginName, pluginUrl) =>
            downloadOrFail(pluginUrl, baseDir / s"$pluginName.jar")
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

  private def downloadOrFail(from: URL, to: File)(implicit log: Logger): Unit =
    if (to.isFile) {
      log.info(s"Skip downloading $from because $to exists")
    } else {
      log.info(s"Downloading $from to $to")
      IO.download(from, to)
    }

  private def downloadOrLog(from: URL, to: File)(implicit log: Logger): Unit =
    try {
      downloadOrFail(from, to)
    } catch {
      case exc: IOException =>
        log.warn(s"Abort downloading $from because of exception: ${exc.getMessage}")
    }

  private def unpack(from: File, to: File)(implicit log: Logger): Unit = {
    log.info(s"Unpacking $from to $to")
    IO.unzip(from, to)
  }
}
