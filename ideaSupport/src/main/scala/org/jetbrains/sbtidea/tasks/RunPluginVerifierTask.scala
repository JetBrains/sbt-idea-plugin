package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.download.FileDownloader
import org.jetbrains.sbtidea.packaging.PackagingKeys.*
import org.jetbrains.sbtidea.runIdea.IntellijAwareRunner
import org.jetbrains.sbtidea.verifier.FailureLevel
import org.jetbrains.sbtidea.{Any2Option, PluginLogger, SbtPluginLogger}
import sbt.Keys.{streams, target}
import sbt.{Def, *}

import java.io.{BufferedReader, File, InputStreamReader}
import java.lang.ProcessBuilder as JProcessBuilder
import java.net.URL
import java.nio.file.Path
import java.util.function.Consumer
import scala.annotation.nowarn
import scala.language.{postfixOps, reflectiveCalls}
import scala.util.{Failure, Try, Using}
import scala.xml.XML

object RunPluginVerifierTask extends SbtIdeaTask[File] {
  class PluginVerificationFailedException extends RuntimeException("Plugin verification failed, see verification report for details")

  def defaultVerifierOptions: Def.Initialize[PluginVerifierOptions] = Def.setting {
    val isRunningOnTC = System.getenv("TEAMCITY_VERSION") != null
    PluginVerifierOptions(
      version             = System.getenv("JBPV_VERSION").lift2Option.getOrElse(latestVerifierVersion),
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

  @nowarn("msg=a pure expression does nothing in statement position")
  override def createTask: Def.Initialize[Task[File]] = sbt.Def.task {
    import scala.collection.JavaConverters.*
    PluginLogger.bind(new SbtPluginLogger(streams.value))
    packageArtifact.value
    val verifierDir   = target.value / "verifier"
    val options       = pluginVerifierOptions.value
    val verifierJar   = getOrDownloadVerifier(options.version, verifierDir)
    val runner        = new IntellijAwareRunner(intellijBaseDirectory.value.toPath, true) {
      private var hasErrors: Boolean = false
      override protected def buildJavaArgs: Seq[String] = Seq(
        "-jar",
        verifierJar.toAbsolutePath.toString
      ) ++ options.buildOptions

      //noinspection ConvertExpressionToSAM : scala 2.10
      override def run(): Int = {
        val processBuilder = new JProcessBuilder()
        val fullCommand = buildFullCommand
        val process = processBuilder
          .command(fullCommand)
          .start()
        PluginLogger.info(s"Started plugin verifier $verifierJar:\n${fullCommand.asScala.mkString(" ")}")
        Using.resource(process.getInputStream) { stream =>
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
        if (hasErrors) -1 else 0
      }
    }
    if (runner.run != 0)
      throw new PluginVerificationFailedException
    else
      options.reportsDir
  }

  private def getOrDownloadVerifier(version: String, downloadDir: File): Path = {
    val targetFile = downloadDir / "downloads" / s"verifier-cli-$version-all.jar"
    if (targetFile.exists()) {
      targetFile.toPath
    } else {
      val repoUrl = s"$BASE_URL/org/jetbrains/intellij/plugins/verifier-cli"
      val artifactUrl = s"$repoUrl/$version/verifier-cli-$version-all.jar"
      new FileDownloader(downloadDir.toPath).download(new URL(artifactUrl))
    }
  }

  private lazy val latestVerifierVersion: String = {
    Try(XML.load(SPACE_METADATA_URL)) match {
      case Failure(exception) =>
        PluginLogger.error(s"failed get latest verifier version: ${exception.getMessage}")
        HARDCODED_VERSION
      case scala.util.Success(value) =>
        val v = (value \\ "metadata" \ "versioning" \ "latest").text
        if (v.isEmpty) {
          PluginLogger.error(s"failed get latest verifier version: falling back to $HARDCODED_VERSION")
          HARDCODED_VERSION
        } else {
          v
        }
    }
  }

  val HARDCODED_VERSION  = "1.369"
  val BASE_URL           = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-verifier"
  val SPACE_METADATA_URL = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-verifier/org/jetbrains/intellij/plugins/verifier-cli/maven-metadata.xml"
}
