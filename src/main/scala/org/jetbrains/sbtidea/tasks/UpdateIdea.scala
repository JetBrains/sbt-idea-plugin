package org.jetbrains.sbtidea.tasks

import java.io.IOException

import gigahorse.HttpClient
import gigahorse.support.okhttp.Gigahorse
import org.jetbrains.sbtidea.Keys._
import sbt.Keys._
import sbt._

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object UpdateIdea {

  def apply(baseDir: File, edition: IdeaEdition, build: String,
            downloadSources: Boolean,
            externalPlugins: Seq[IdeaPlugin],
            streams: TaskStreams): Unit = {
    implicit val log: Logger = streams.log

    Gigahorse.withHttp { implicit http =>

      val baseVerificationFile = baseDir / "download.complete"
      val sourcesFile = baseDir / "sources.zip"
      val sourcesVerificationFile = baseDir / "sources.download.complete"

      val downloadedBase: Future[File] =
        if (baseDir.isDirectory && baseVerificationFile.isFile ) {
          // TODO check some verification hash of completed unpacking
          log.debug(s"Skip downloading and unpacking IDEA because $baseDir exists")
          Future.successful(baseDir)
        } else
          downloadIdeaBinaries(baseDir, edition, build)
            .map(_ => baseDir)
            .andThen {
              case Success(_) =>
                baseVerificationFile.createNewFile()
                log.info(s"IDEA binaries downloaded to ${baseDir.getAbsolutePath}")
              case Failure(x) => log.error("failed to download IDEA binaries: " + x.getMessage)
            }

      val downloadedSources: Future[File] =
        if (downloadSources) {
          if (sourcesFile.isFile && sourcesVerificationFile.isFile){
            log.debug(s"Skip downloading IDEA sources because $sourcesFile exists")
            Future.successful(sourcesFile)
          }
          else
            downloadIdeaSources(sourcesFile, build)
              .andThen {
                case Success(file) =>
                  sourcesVerificationFile.createNewFile()
                  log.info(s"IDEA sources downloaded to $file")
                case Failure(x) => log.error("failed to download IDEA sources: " + x.getMessage)
              }
        } else {
          Future.successful(sourcesFile)
        }

      val externalPluginsDir = baseDir / "externalPlugins"
      downloadExternalPlugins(externalPluginsDir, externalPlugins)
      movePluginsIntoRightPlace(externalPluginsDir, externalPlugins)

      Await.ready(Future.sequence(Seq(downloadedBase, downloadedSources)), 20.minutes)
    }

  }

  private def downloadIdeaBinaries(baseDir: File, edition: IdeaEdition, build: String)(implicit log: Logger, http: HttpClient): Future[File] = {
    // TODO some verification that downloading/unpacking was successful
    val repositoryUrl = getRepositoryForBuild(build)
    val ideaUrl = url(s"$repositoryUrl/${edition.name}/$build/${edition.name}-$build.zip")
    val ideaZipFile = baseDir.getParentFile / s"${edition.name}-$build.zip"
    downloadOrFail(ideaUrl, ideaZipFile)
      .flatMap { file =>
        if (unpack(file, baseDir).nonEmpty) {
          file.delete()
          Future.successful(file)
        }
        else Future.failed(new Exception(s"archive $file contained no files"))
      }
  }

  private def downloadIdeaSources(sourcesFile: File, build: String)(implicit log: Logger, httpClient: HttpClient): Future[File] = {
    val repositoryUrl = getRepositoryForBuild(build)
    val ideaSourcesJarUrl = url(s"$repositoryUrl/ideaIC/$build/ideaIC-$build-sources.jar")
    val ideaSourcesZipUrl = url(s"$repositoryUrl/ideaIC/$build/ideaIC-$build-sources.zip")

    downloadOrFail(ideaSourcesJarUrl, sourcesFile).recoverWith {
      case _: IOException =>
        downloadOrFail(ideaSourcesZipUrl, sourcesFile)
    }
  }

  private def getRepositoryForBuild(build: String): String = {
    val repository = if (build.endsWith("SNAPSHOT")) "snapshots" else "releases"
    s"https://www.jetbrains.com/intellij-repository/$repository/com/jetbrains/intellij/idea"
  }

  private def downloadExternalPlugins(baseDir: File, plugins: Seq[IdeaPlugin])(implicit log: Logger, httpClient: HttpClient): Unit =
    plugins.foreach { plugin =>
      val pluginDir = baseDir / plugin.name
      if (pluginDir.isDirectory)
        log.debug(s"Skip downloading ${plugin.name} external plugin because $pluginDir exists")
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
      val childDirs = listDirectories(pluginDir)
      if (childDirs.forall(_.getName != "lib")) {
        val dirThatContainsLib = childDirs.find(d => listDirectories(d).exists(_.getName == "lib"))
        dirThatContainsLib.foreach { dir =>
          IO.copyDirectory(dir, pluginDir)
          IO.delete(dir)
        }
      }
    }

  private def listDirectories(dir: File): Seq[File] =
    Option(dir.listFiles).toSeq.flatten.filter(_.isDirectory)

  /**
    * Downloads file and creates verification file if successful
    */
  private def downloadOrFail(from: URL, to: File)(implicit log: Logger, http: HttpClient): Future[File] =
    if (to.isFile) {
      log.debug(s"Skip downloading $from because $to exists")
      Future.successful(to)
    } else {
      IO.createDirectory(to.getParentFile)
      log.info(s"Downloading $from to $to")
      download(from, to)
    }

  private def unpack(from: File, to: File)(implicit log: Logger): Set[File] = {
    log.info(s"Unpacking $from to $to")
    sbt.IO.unzip(from, to)
  }

  private def download(from: sbt.URL, to: File)(implicit http: HttpClient): Future[File] = {
    val req = Gigahorse.url(from.toString)
    http.download(req, to)
  }

  private def verifyFile(file: File): File =
    file.getParentFile / (file.getName + ".verify")

}
