package org.jetbrains.sbtidea.integrationTests

import org.apache.commons.io.FileUtils
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils.runSbtProcess
import org.jetbrains.sbtidea.testUtils.{CurrentEnvironmentUtils, FileAssertions, SbtProjectFilesUtils}
import org.scalatest.funsuite.AnyFunSuite
import sbt.{File, fileToRichFile}

abstract class SbtIdeaPluginIntegrationTestBase
  extends AnyFunSuite
    with FileAssertions {

  protected val testProjectsDir: File = new File("ideaSupport/testData/projects").getAbsoluteFile
  protected val intellijSdksBaseDir: File = new File("tempIntellijSdks").getAbsoluteFile
  protected lazy val pluginVersion: String = CurrentEnvironmentUtils.publishCurrentSbtIdeaPluginToLocalRepoAndGetVersions
  protected val commonIntellijBuild: String = "243.22562.145"

  protected def cleanSdkRootBeforeUpdate: Boolean = false

  protected final def runUpdateIntellijCommand(testProjectDirName: String): File =
    runUpdateIntellijCommand(testProjectsDir / testProjectDirName)

  protected final def runUpdateIntellijCommand(projectDir: File): File = {
    assertFileExists(projectDir)

    val sdkRoot = intellijSdksBaseDir / projectDir.getName
    if (cleanSdkRootBeforeUpdate) {
      FileUtils.deleteDirectory(sdkRoot)
    } else {
      printNoSdkCleanupWarning(projectDir, sdkRoot)
    }

    SbtProjectFilesUtils.cleanUntrackedVcsFiles(projectDir)
    SbtProjectFilesUtils.updateSbtIdeaPluginToVersion(projectDir, pluginVersion)

    val intellijSdkRoot = SbtProjectFilesUtils.injectExtraSbtFileWithIntelliJSdkTargetDirSettingsForSdkRoot(projectDir, sdkRoot)

    runSbtProcess(
      Seq("updateIntellij"),
      projectDir,
      vmOptions = Seq("-Dsbt.idea.plugin.keep.downloaded.files=true"),
    )

    intellijSdkRoot / "sdk" / commonIntellijBuild
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
