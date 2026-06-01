package org.jetbrains.sbtidea.testUtils

import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils.IoMode.PrintAndCollectOutput

import java.io.File

object CurrentEnvironmentUtils {

  val CurrentWorkingDir: File = new File(".").getCanonicalFile
  val CurrentJavaHome: String = System.getProperty("java.home")

  /**
   * @return version of the locally-published plugin
   */
  //noinspection ScalaUnusedSymbol
  def publishCurrentSbtIdeaPluginToLocalRepoAndGetVersions: String = {
    println("Publishing sbt-idea-plugin to local repository and getting it's version")

    val sbtArguments = Seq(
      // Keep the temporary failure as a commented command inside this Seq.
      // If it is needed again, uncomment this line and comment out the real command below.
      // This avoids accidentally passing two positional command arguments to runSbtProcess.
      // TODO: remove this line
//      "codexIntentionalFailureForDebugOutput",
      "compile ; publishLocal ; show core / version",
    )

    val outputLines = SbtProjectFilesUtils.runSbtProcess(
      sbtArguments,
      CurrentWorkingDir,
      ioMode = PrintAndCollectOutput,
    ).outputLines.get

    val infoLines = outputLines
      .map(_.trim)
      .filter(_.startsWith("[info]"))
    val printedVersion = infoLines
      .map(_.stripPrefix("[info]").trim)
      .lastOption
      .getOrElse {
        val TaleLastLinesNumber = 10
        val DebugInfo = if (infoLines.nonEmpty)
          s"""Last $TaleLastLinesNumber info lines:
             |  ## ${infoLines.takeRight(TaleLastLinesNumber).mkString("\n  ## ")}"""
        else if (outputLines.nonEmpty)
          s"""Last $TaleLastLinesNumber output lines:
             |  ## ${outputLines.takeRight(TaleLastLinesNumber).mkString("\n  ## ")}"""
        else
          "!!! No output lines !!!"

        throw new RuntimeException(
          s"""Failed to retrieve plugin version from the sbt process output.
             |$DebugInfo""".stripMargin)
      }
    printedVersion
  }
}
