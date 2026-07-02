package org.jetbrains.sbtidea.integrationTests

import org.apache.commons.io.FileUtils
import org.jetbrains.sbtidea.TmpDirUtils
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils.runSbtProcess
import org.jetbrains.sbtidea.testUtils.{CurrentEnvironmentUtils, FileAssertions, SbtProjectFilesUtils}
import org.scalatest.funsuite.AnyFunSuite
import sbt.{File, fileToRichFile}

import java.nio.file.{Files, StandardCopyOption}
import scala.io.Source

abstract class SbtIdeaPluginIntegrationTestBase
  extends AnyFunSuite
    with FileAssertions
    with TmpDirUtils {

  protected val testProjectsDir: File = new File("ideaSupport/testData/projects").getAbsoluteFile
  protected val intellijSdksBaseDir: File = new File("tempIntellijSdks").getAbsoluteFile
  protected lazy val pluginVersion: String = CurrentEnvironmentUtils.publishCurrentSbtIdeaPluginToLocalRepoAndGetVersions
  protected val commonIntellijBuild: String = "243.22562.145"

  protected final class UpdatedTestProject(val projectDir: File, val intellijSdkRoot: File)

  protected def cleanSdkRootBeforeUpdate: Boolean = false

  protected final def runUpdateIntellijCommand(testProjectDirName: String): File =
    runUpdateIntellijCommandInTemporaryProject(testProjectDirName).intellijSdkRoot

  protected final def runUpdateIntellijCommandInTemporaryProject(testProjectDirName: String): UpdatedTestProject = {
    val fixtureProjectDir = testProjectsDir / testProjectDirName
    assertFileExists(fixtureProjectDir)

    val projectDir = copyTestProjectToTemporaryDirectory(fixtureProjectDir)
    val intellijSdkRoot = runUpdateIntellijCommandInPreparedProject(projectDir)

    new UpdatedTestProject(projectDir, intellijSdkRoot)
  }

  private def runUpdateIntellijCommandInPreparedProject(projectDir: File): File = {
    assertFileExists(projectDir)

    val sdkRoot = intellijSdksBaseDir / projectDir.getName
    if (cleanSdkRootBeforeUpdate) {
      FileUtils.deleteDirectory(sdkRoot)
    } else {
      printNoSdkCleanupWarning(projectDir, sdkRoot)
    }

    SbtProjectFilesUtils.updateSbtVersion(projectDir, SbtProjectFilesUtils.SbtVersionForIntegrationTests)
    SbtProjectFilesUtils.updateSbtIdeaPluginToVersion(projectDir, pluginVersion)

    val intellijSdkRoot = SbtProjectFilesUtils.injectExtraSbtFileWithIntelliJSdkTargetDirSettingsForSdkRoot(projectDir, sdkRoot)

    runSbtProcess(
      Seq("updateIntellij"),
      projectDir,
      vmOptions = Seq("-Dsbt.idea.plugin.keep.downloaded.files=true"),
    )

    intellijSdkRoot / "sdk" / commonIntellijBuild
  }

  private def copyTestProjectToTemporaryDirectory(sourceProjectDir: File): File = {
    val targetProjectDir = newTmpDir.toFile / sourceProjectDir.getName
    val trackedFixtureFiles = listTrackedFixtureFiles(sourceProjectDir)

    if (trackedFixtureFiles.nonEmpty) {
      copyFilesPreservingRelativePaths(sourceProjectDir, targetProjectDir, trackedFixtureFiles)
    } else {
      FileUtils.copyDirectory(sourceProjectDir, targetProjectDir, generatedTestProjectCopyFilter(sourceProjectDir))
    }

    targetProjectDir
  }

  private def copyFilesPreservingRelativePaths(sourceProjectDir: File, targetProjectDir: File, sourceFiles: Seq[File]): Unit = {
    val sourceProjectPath = sourceProjectDir.getCanonicalFile.toPath

    sourceFiles.foreach { sourceFile =>
      val relativePath = sourceProjectPath.relativize(sourceFile.getCanonicalFile.toPath)
      val targetFile = targetProjectDir.toPath.resolve(relativePath)
      val targetParent = targetFile.getParent

      if (targetParent != null) {
        Files.createDirectories(targetParent)
      }
      Files.copy(sourceFile.toPath, targetFile, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def listTrackedFixtureFiles(sourceProjectDir: File): Seq[File] = {
    try {
      val repoRoot = CurrentEnvironmentUtils.CurrentWorkingDir.getCanonicalFile
      val relativeProjectPath = repoRoot.toPath.relativize(sourceProjectDir.getCanonicalFile.toPath).toString
      val process = new ProcessBuilder("git", "ls-files", "--", relativeProjectPath)
        .directory(repoRoot)
        .redirectErrorStream(true)
        .start()

      val outputLines =
        try Source.fromInputStream(process.getInputStream).getLines().toVector
        finally process.getInputStream.close()

      if (process.waitFor() == 0)
        outputLines.map(repoRoot / _).filter(_.isFile)
      else
        Seq.empty
    } catch {
      case _: Exception => Seq.empty
    }
  }

  private def generatedTestProjectCopyFilter(sourceProjectDir: File): java.io.FileFilter = { file =>
    val relativePath = sourceProjectDir.getCanonicalFile.toPath.relativize(file.getCanonicalFile.toPath)

    if (relativePath.toString.isEmpty) {
      true
    } else {
      val names = (0 until relativePath.getNameCount).map(relativePath.getName(_).toString)
      val rootGeneratedFile = relativePath.getNameCount == 1 && relativePath.getFileName.toString == "extra.sbt"
      val generatedDir = names.exists(name => name == "target" || name == ".idea" || name == ".bsp" || name == ".bloop")
      val generatedNestedProjectDir = names.length >= 2 && names.head == "project" && (names(1) == "target" || names(1) == "project")

      !rootGeneratedFile && !generatedDir && !generatedNestedProjectDir
    }
  }

  private def printNoSdkCleanupWarning(projectDir: File, sdkRoot: File): Unit = {
    printWarningWithOrangeColor(
      s"""##########################################################################################################
         |#   WARNING: SDK CLEANUP IS DISABLED FOR INTEGRATION TESTS - REUSING SDK ROOT: $sdkRoot                  #
         |#   Project: $projectDir                                                                                 #
         |#   This run may reuse pre-existing IDEA/JBR/plugin installation and can hide reinstall-related issues.  #
         |#   To run 100% clean tests, remove the SDK directory: $sdkRoot                                          #
         |##########################################################################################################""".stripMargin
    )
  }

  private def printWarningWithOrangeColor(text: String): Unit = {
    println("\u001b[38;5;208m")
    println(text)
    println("\u001b[0m")
  }
}
