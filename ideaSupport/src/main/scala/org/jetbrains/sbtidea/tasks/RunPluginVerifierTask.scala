package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.download.FileDownloader
import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.sbtidea.packaging.artifact
import org.jetbrains.sbtidea.runIdea.IntellijAwareRunner
import org.jetbrains.sbtidea.verifier.FailureLevel
import org.jetbrains.sbtidea.{Any2Option, PluginLogger, SbtPluginLogger}
import sbt.Keys.{streams, target}
import sbt.{Def, _}

import java.io.{BufferedReader, File, InputStreamReader}
import java.lang.{ProcessBuilder => JProcessBuilder}
import java.net.URL
import java.nio.file.Path
import java.util.function.Consumer
import scala.language.postfixOps
import scala.language.reflectiveCalls

object RunPluginVerifierTask extends SbtIdeaTask[File] {
  class PluginVerificationFailedException extends RuntimeException("Plugin verification failed, see verification report for details")

  def defaultVerifierOptions: Def.Initialize[PluginVerifierOptions] = Def.setting {
    val isRunningOnTC = System.getenv("TEAMCITY_VERSION") != null
    PluginVerifierOptions(
      version             = System.getenv("JBPV_VERSION").lift2Option.getOrElse(fetchLatestVerifierVersion),
      reportsDir          = target.value / "verifier" / "reports",
      teamcity            = isRunningOnTC,
      teamcityGrouping    = isRunningOnTC,
      offline             = false,
      pluginDir           = packageOutputDir.value,
      ideaDir             = intellijBaseDirectory.in(ThisBuild).value,
      failureLevels       = FailureLevel.values().toSet,
      additionalCommonOpts= Seq.empty,
      overrideIDEs        = Seq.empty
    )
  }

  override def createTask: Def.Initialize[Task[File]] = sbt.Def.task {
    PluginLogger.bind(new SbtPluginLogger(streams.value))
    packageArtifact.value
    val verifierDir   = target.value / "verifier"
    val options       = pluginVerifierOptions.value
    val verifierJar   = getOrDownloadVerifier(options.version, verifierDir)
    val ideaCP        = intellijMainJars.value.map(_.data.toPath)
    val runner        = new IntellijAwareRunner(ideaCP, true) {
      var hasErrors: Boolean = false
      override protected def buildJavaArgs: Seq[String] = Seq(
        "-jar",
        verifierJar.toAbsolutePath.toString
      ) ++ options.buildOptions

      //noinspection ConvertExpressionToSAM : scala 2.10
      override def run(): Int = {
        val processBuilder = new JProcessBuilder()
        val process = processBuilder
          .command(buildFullCommand)
          .start()

        artifact.using(process.getInputStream) { stream =>
          val reader = new BufferedReader(new InputStreamReader(stream))
          reader
            .lines()
            .forEach(new Consumer[String] {
              override def accept(line: String): Unit = {
                println(line)
                hasErrors = hasErrors || options.failureLevels.exists(lvl => line.startsWith(lvl.testValue))
              }
            })
        }
        process.waitFor()
      }
    }
    PluginLogger.info(s"running plugin verifier $verifierJar")
    if (runner.hasErrors)
      throw new PluginVerificationFailedException
    else
      options.reportsDir
  }

  // TODO: migrate from bintray
  private def getOrDownloadVerifier(version: String, downloadDir: File): Path = {
    val targetFile = downloadDir / "downloads" / s"verifier-cli-$version-all.jar"
    if (targetFile.exists()) {
      targetFile.toPath
    } else {
      val baseUrl = "https://dl.bintray.com/jetbrains/intellij-plugin-service"
      val repoUrl = s"$baseUrl/org/jetbrains/intellij/plugins/verifier-cli"
      val artifactUrl = s"$repoUrl/$version/verifier-cli-$version-all.jar"
      new FileDownloader(downloadDir.toPath).download(new URL(artifactUrl))
    }
  }

  // TODO: implement after migration from bintray
  private def fetchLatestVerifierVersion: String = "1.254"
}
