package org.jetbrains.sbtidea.testUtils

import java.io.File
import scala.util.Using

object CurrentEnvironmentUtils {

  val CurrentWorkingDir: File = new File(".").getCanonicalFile
  val CurrentJavaHome: String = System.getProperty("java.home")

  /**
   * @return version of the locally-published plugin
   */
  //noinspection ScalaUnusedSymbol
  def publishCurrentSbtIdeaPluginToLocalRepoAndGetVersions: String = {
    println("Publishing sbt-idea-plugin to local repository and getting it's version")

    val process = new ProcessBuilder("sbt", "compile ; publishLocal ; show core / version")
      .directory(CurrentWorkingDir)
      .redirectErrorStream(true)
      .start()

    val outputLines = Using.resource(scala.io.Source.fromInputStream(process.getInputStream)) { source =>
      source.getLines().map { line =>
       println(line)
       line
     }.toArray.toSeq
    }

    process.waitFor()

    val exitCode = process.exitValue()
    if (exitCode != 0)
      throw new RuntimeException(s"Failed to execute sbt command to detect current sbt-idea-plugin version (exit code: $exitCode)")

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
        else if (outputLines.nonEmpty) {
          s"""Last $TaleLastLinesNumber output lines:
             |  ## ${outputLines.takeRight(TaleLastLinesNumber).mkString("\n  ## ")}"""
        } else {
          "!!! No output lines !!!"
        }
        throw new RuntimeException(
          s"""Failed to retrieve plugin version from the sbt process output.
             |$DebugInfo""".stripMargin)
      }
    printedVersion
  }
}
