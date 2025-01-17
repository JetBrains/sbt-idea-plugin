package org.jetbrains.sbtidea.testUtils

import java.io.File

object CurrentEnvironmentUtils {

  /**
   * @return version of the locally-published plugin
   */
  //noinspection ScalaUnusedSymbol
  def publishCurrentSbtIdeaPluginToLocalRepoAndGetVersions: String = {
    println("Publishing sbt-idea-plugin to local repository...")

    val workingDir = new File(".").getCanonicalFile
    val process = new ProcessBuilder("sbt", "compile ; publishLocal ; show core / version")
      .directory(workingDir)
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

    val printedVersion = outputLines
      .collect { case line if line.startsWith("[info]") => line.stripPrefix("[info]").trim }
      .lastOption
      .getOrElse {
        "Failed to retrieve both plugin and sbt versions from the sbt process output."
      }
    printedVersion
  }
}
