package org.jetbrains.sbtidea.testUtils

import sbt.fileToRichFile

import java.io.File
import java.nio.file.Path
import scala.collection.mutable
import scala.io.Source
import scala.util.Using

object SbtProjectFilesUtils {
  // Keep failure messages useful without embedding huge sbt logs into ScalaTest output.
  private val ProcessOutputTailLinesNumber = 80
  val SbtVersionForIntegrationTests = "1.12.11"

  def updateSbtIdeaPluginToVersion(projectDir: File, sbtIdePluginVersion: String): Path = {
    val pluginsSbtFile = projectDir / "project" / "plugins.sbt"

    if (!pluginsSbtFile.exists()) {
      // ensure the file exists
      pluginsSbtFile.getParentFile.mkdirs()
      pluginsSbtFile.createNewFile()
    }

    val content = IoUtils.readLines(pluginsSbtFile)
    val contentWithoutPlugin = content
      .filterNot(_.contains("""addSbtPlugin("org.jetbrains.scala" % "sbt-idea-plugin""""))
      .mkString("\n")

    val contentUpdated =
      s"""$contentWithoutPlugin
         |addSbtPlugin("org.jetbrains.scala" % "sbt-idea-plugin" % "$sbtIdePluginVersion")
         |""".stripMargin.trim

    IoUtils.writeStringToFile(pluginsSbtFile, contentUpdated)
  }

  def updateSbtVersion(repoDir: File, newSbtVersion: String): Unit = {
    val sbtPropertiesFile = repoDir / "project" / "build.properties"
    sbtPropertiesFile.getParentFile.mkdirs()
    val sbtVersionInRepo =
      if (sbtPropertiesFile.exists()) {
        IoUtils.readLines(sbtPropertiesFile)
          .find(_.startsWith("sbt.version"))
          .map(_.split("=").apply(1).trim)
          .getOrElse("<unknown>")
      } else {
        "<missing>"
      }

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
    val DebugAgentOption = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:0"
    val effectiveVmOptions = vmOptions :+ DebugAgentOption
    val javaOptions = if (effectiveVmOptions.nonEmpty) Map("JAVA_OPTS" -> effectiveVmOptions.mkString(" ")) else Map.empty
    val envVarsUpdated = envVars ++ javaOptions

    val sbtExecutablePath = sys.env.get("SBT_PATH")
      .orElse(Option(System.getProperty("sbt.executable.path")))
      .getOrElse("sbt")
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
    pb.redirectInput(ProcessBuilder.Redirect.INHERIT)

    envVars.foreach { case (key, value) =>
      pb.environment().put(key, value)
    }

    val process = pb.start()

    // IoMode.Inherit used to print process output via ProcessBuilder.inheritIO().
    // We still print it live, but stdout/stderr are now piped through this helper
    // so nonzero exits can include the last output lines in the thrown exception.
    val outputTail = mutable.Queue.empty[String]

    // Only callers that explicitly request collected output should receive the full log.
    // Other callers still get the bounded failure tail via outputTail.
    val collectedOutputBuilder: Option[mutable.Builder[String, Vector[String]]] =
      if (ioMode == IoMode.PrintAndCollectOutput)
        Some(Vector.newBuilder[String])
      else
        None
    var outputLineCount = 0

    val outputLines: Option[Seq[String]] = Using.resource(Source.fromInputStream(process.getInputStream)) { source =>
      source.getLines.foreach { line =>
        println(line)

        outputLineCount += 1
        collectedOutputBuilder.foreach(_ += line)

        // Keep the last N lines so nonzero exits include the relevant sbt error.
        outputTail.enqueue(line)
        if (outputTail.size > ProcessOutputTailLinesNumber) {
          outputTail.dequeue()
        }
      }

      collectedOutputBuilder.map(_.result())
    }

    val exitCode = process.waitFor()
    if (exitCode != 0) {
      throw new RuntimeException(s"Command '$command' failed with exit code $exitCode.${formatProcessOutputForError(outputTail, outputLineCount)}")
    }

    ProcessRunResult(outputLines)
  }

  private def formatProcessOutputForError(lines: Seq[String], outputLineCount: Int): String = {
    if (lines.nonEmpty) {
      // Mention omitted lines so the reader knows this is a tail, not the full process log.
      val omittedLines = outputLineCount - lines.size
      val omittedMessage = if (omittedLines > 0)
        s"\n... ($omittedLines earlier output lines omitted)"
      else
        ""

      s"""$omittedMessage
         |Process output (last ${lines.size} of $outputLineCount lines):
         |${lines.mkString("\n")}""".stripMargin
    } else {
      "\nProcess output: <empty>"
    }
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
    injectExtraSbtFileWithIntelliJSdkTargetDirSettingsForSdkRoot(projectDir, intellijSdkRoot)
  }

  /**
   * Add an `extra.sbt` file to the project.<br>
   * Inside, we inject the location of the downloaded sdk & temp downloads directory
   */
  def injectExtraSbtFileWithIntelliJSdkTargetDirSettingsForSdkRoot(
    projectDir: File,
    intellijSdkRoot: File,
  ): File = {
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
