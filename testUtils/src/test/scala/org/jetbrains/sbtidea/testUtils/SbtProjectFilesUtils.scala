package org.jetbrains.sbtidea.testUtils

import sbt.fileToRichFile

import java.io.File
import java.nio.file.Path
import scala.io.Source
import scala.util.Using

object SbtProjectFilesUtils {

  def updateSbtIdeaPluginToVersion(projectDir: File, sbtIdePluginVersion: String): Path = {
    val pluginsSbtFile = projectDir / "project" / "plugins.sbt"

    if (!pluginsSbtFile.exists()) {
      // ensure the file exists
      pluginsSbtFile.getParentFile.mkdirs()
      pluginsSbtFile.createNewFile()
    }

    val content = IoUtils.readLines(pluginsSbtFile)
    val contentWithoutPlugin = content
      .filterNot(_.contains("""addSbtPlugin("org.jetbrains" % "sbt-idea-plugin""""))
      .mkString("\n")

    val contentUpdated =
      s"""$contentWithoutPlugin
         |addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "$sbtIdePluginVersion")
         |""".stripMargin.trim

    IoUtils.writeStringToFile(pluginsSbtFile, contentUpdated)
  }

  def updateSbtVersion(repoDir: File, newSbtVersion: String): Unit = {
    val sbtPropertiesFile = repoDir / "project" / "build.properties"
    assert(sbtPropertiesFile.exists())
    val sbtVersionInRepo = IoUtils.readLines(sbtPropertiesFile)
      .find(_.startsWith("sbt.version"))
      .map(_.split("=").apply(1).trim)
      .get

    val updatedContent = s"sbt.version=$newSbtVersion"
    IoUtils.writeStringToFile(sbtPropertiesFile, updatedContent)
    println(s"Updated sbt.version: $sbtVersionInRepo -> $newSbtVersion")
  }

  def cleanUntrackedVcsFiles(projectDir: File): Unit = {
    runProcess(Seq("git", "clean", "-fdx"), projectDir)
  }

  def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) {
      file.listFiles.foreach(deleteRecursively)
    }
    file.delete()
  }

  def runSbtProcess(
    sbtArguments: Seq[String],
    workingDir: File,
    ioMode: IoMode = IoMode.Inherit,
    vmOptions: Seq[String] = Seq.empty,
    envVars: Map[String, String] = Map.empty,
  ): ProcessRunResult = {
    val javaOptions = if (vmOptions.nonEmpty) Map("JAVA_OPTS" -> vmOptions.mkString(" ")) else Map.empty
    val envVarsUpdated = envVars ++ javaOptions

    val sbtExecutablePath = System.getProperty("sbt.executable.path", "sbt")
    runProcess(
      // Disable colors to avoid escape sequences in the output
      // This is needed to parse the output of the test reliably
      Seq(sbtExecutablePath, "-no-colors") ++ sbtArguments,
      workingDir,
      ioMode = ioMode,
      envVars = envVarsUpdated,
    )
  }

  case class ProcessRunResult(outputLines: Option[Seq[String]])

  sealed trait IoMode
  object IoMode {
    object Inherit extends IoMode
    object PrintAndCollectOutput extends IoMode
  }

  def runProcess(
    command: Seq[String],
    workingDir: File,
    ioMode: IoMode = IoMode.Inherit,
    envVars: Map[String, String] = Map.empty
  ): ProcessRunResult = {
    val pb = new ProcessBuilder(command *)
    pb.directory(workingDir)
    pb.redirectErrorStream(true)

    if (ioMode == IoMode.Inherit) {
      pb.inheritIO()
    }

    envVars.foreach { case (key, value) =>
      pb.environment().put(key, value)
    }

    val process = pb.start()

    val outputLines: Option[Seq[String]] = if (ioMode == IoMode.PrintAndCollectOutput) {
      Some(Using.resource(Source.fromInputStream(process.getInputStream)) { source =>
        source.getLines.map { line =>
          println(line)
          line
        }.toArray.toSeq
      })
    } else {
      None
    }

    val exitCode = process.waitFor()
    if (exitCode != 0) {
      throw new RuntimeException(s"Command '$command' failed with exit code $exitCode")
    }

    ProcessRunResult(outputLines)
  }

  /**
   * Add an `extra.sbt` file to the project.<br>
   * Inside, we inject the location of the downloaded sdk & temp downloads directory
   */
  def injectExtraSbtFileWithIntelliJSdkTargetDirSettings(
    projectDir: File,
    sdksBaseDir: File,
  ): File = {
    // Use subdirectory with same name as the original project
    val intellijSdkRoot = sdksBaseDir / projectDir.getName
    // Store downloads in the same dir for all projects as a cache when the same artifacts are used in the tests
    val intellijSdkDownloadDir = CurrentEnvironmentUtils.CurrentWorkingDir / "tempIntellijArtifactsDownloads"
    println(
      s"""Intellij SDK root: $intellijSdkRoot
         |Intellij SDK download dir: $intellijSdkDownloadDir
         |""".stripMargin.trim
    )
    IoUtils.writeStringToFile(
      projectDir / "extra.sbt",
      s"""import org.jetbrains.sbtidea.Keys._
         |
         |ThisBuild / intellijPluginDirectory := file("$intellijSdkRoot")
         |ThisBuild / artifactsDownloadsDir   := file("$intellijSdkDownloadDir")
         |""".stripMargin
    )
    intellijSdkRoot
  }
}