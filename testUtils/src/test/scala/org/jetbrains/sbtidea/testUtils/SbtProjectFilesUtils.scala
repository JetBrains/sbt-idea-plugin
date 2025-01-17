package org.jetbrains.sbtidea.testUtils

import sbt.fileToRichFile

import java.io.File
import java.nio.file.Path
import scala.util.chaining.scalaUtilChainingOps

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

  def gitCleanUntrackedFiles(projectDir: File): Unit = {
    runProcess(Seq("git", "clean", "-fdx"), projectDir)

    //TODO: it seems it's not needed as "x" argument in "git clean -fdx" already does the job
    // remove all sbt "target" directories or empty directories (left after git restore)
//    Files.walk(projectDir.toPath)
//      .filter { path =>
//        Files.isDirectory(path) && path.getFileName.toString == "target" ||
//          Files.isDirectory(path) && Files.list(path).count() == 0
//      }
//      .forEach(path => deleteRecursively(path.toFile))
  }

  def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) {
      file.listFiles.foreach(deleteRecursively)
    }
    file.delete()
  }


  def runProcess(
    command: Seq[String],
    workingDir: File,
    envVars: Map[String, String] = Map.empty // Add envVars parameter with a default value
  ): Unit = {
    val process = new ProcessBuilder(command *)
      .directory(workingDir)
      .inheritIO()
      .redirectErrorStream(true)
      .tap(pb => envVars.foreach { case (key, value) => pb.environment().put(key, value) }) // Use .tap to set env variables
      .start()

    val exitCode = process.waitFor()
    if (exitCode != 0) {
      throw new RuntimeException(s"Command '$command' failed with exit code $exitCode")
    }
  }
  def runProcess(
    command: Seq[String],
    workingDir: File,
  ): Unit = {
    val process = new ProcessBuilder(command *)
      .directory(workingDir)
      .inheritIO()
      .redirectErrorStream(true)
      .start()

    val exitCode = process.waitFor()
    if (exitCode != 0) {
      throw new RuntimeException(s"Command '$command' failed with exit code $exitCode")
    }
  }
}