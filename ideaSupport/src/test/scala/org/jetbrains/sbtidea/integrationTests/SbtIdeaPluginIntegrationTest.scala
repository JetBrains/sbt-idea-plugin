package org.jetbrains.sbtidea.integrationTests

import org.jetbrains.sbtidea.download.api.IdeInstallationContext
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils.runProcess
import org.jetbrains.sbtidea.testUtils.{CurrentEnvironmentUtils, FileAssertions, IoUtils, SbtProjectFilesUtils}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sbt.{File, fileToRichFile}

class SbtIdeaPluginIntegrationTest
  extends AnyFunSuite
    with Matchers
    with FileAssertions {

  private val TestProjectsDir = new File("ideaSupport/testData/projects").getAbsoluteFile
  private val IntellijSdksBaseDir = new File("tempIntellijSdks").getAbsoluteFile

  private val PluginVersion = CurrentEnvironmentUtils.publishCurrentSbtIdeaPluginToLocalRepoAndGetVersions

  private val CommonIntellijBuild = "243.22562.145"

  private def doCommonAssertions(intellijSdkRoot: File): Unit = {
    assertFileExists(intellijSdkRoot)
    assertFileExists(intellijSdkRoot / "lib")
    assertFileExists(intellijSdkRoot / "plugins")
    assertFileExists(intellijSdkRoot / "product-info.json")
    assertFileExists(intellijSdkRoot / "sources.zip")
  }

  test("simple-with-plugin") {
    val intellijSdkRoot = runUpdateIntellijCommand("simple-with-plugin")

    doCommonAssertions(intellijSdkRoot)
    assertFileExists(intellijSdkRoot / "plugins" / "scala")
    new IdeInstallationContext(intellijSdkRoot.toPath).productInfo.productCode shouldBe "IC"
  }

  test("simple-ultimate-edition") {
    val intellijSdkRoot = runUpdateIntellijCommand("simple-ultimate-edition")

    doCommonAssertions(intellijSdkRoot)
    assertFileDoesNotExist(intellijSdkRoot / "plugins" / "scala")
    new IdeInstallationContext(intellijSdkRoot.toPath).productInfo.productCode shouldBe "IU"
  }

  /**
   * @return root of ide installation
   */
  private def runUpdateIntellijCommand(testProjectDirName: String): File = {
    val projectDir = TestProjectsDir / testProjectDirName
    assertFileExists(projectDir)

    SbtProjectFilesUtils.gitCleanUntrackedFiles(projectDir)
    SbtProjectFilesUtils.updateSbtIdeaPluginToVersion(projectDir, PluginVersion)

    // Inject location of the downloaded sdk
    val intellijSdkRoot = IntellijSdksBaseDir / testProjectDirName
    // Store downloads in the same dir for all projects as a cache when same artefacts are used in the tests
    val intellijSdkDownloadDir = IntellijSdksBaseDir / "downloads"
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

    runProcess(
      Seq("sbt", "updateIntellij"),
      projectDir,
      //ensure we reuse downloaded artefacts between tests if they need the same artifacts
      envVars = Map("JAVA_OPTS" -> "-Dsbt.idea.plugin.keep.downloaded.files=true")
    )

    intellijSdkRoot / "sdk" / CommonIntellijBuild
  }
}