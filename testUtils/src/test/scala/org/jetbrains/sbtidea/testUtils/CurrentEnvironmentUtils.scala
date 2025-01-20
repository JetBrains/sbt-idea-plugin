package org.jetbrains.sbtidea.testUtils

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

    val process = new ProcessBuilder("sbt", "compile ; publishLocal ; show core / version")
      .directory(CurrentWorkingDir)
      .redirectErrorStream(true)
      .start()

    val outputLines = scala.io.Source.fromInputStream(process.getInputStream).getLines().map { line =>
      println(line)
      line
    }.toList

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
        val LastInfoLinesNumber = 10
        throw new RuntimeException(
          s"""Failed to retrieve plugin version from the sbt process output.
             |Last $LastInfoLinesNumber info lines:
             |  ${infoLines.takeRight(LastInfoLinesNumber).mkString("\n  ")}""".stripMargin)
      }
    printedVersion
  }
}
