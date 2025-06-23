package org.jetbrains.sbtidea.integrationTests

import org.jetbrains.sbtidea.download.api.IdeInstallationContext
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils.{runProcess, runSbtProcess}
import org.jetbrains.sbtidea.testUtils.{CurrentEnvironmentUtils, FileAssertions, SbtProjectFilesUtils}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sbt.{File, fileToRichFile}

/**
 * This test is designed to test twe work of sbt-idea-plugin as a whole, in real projects.
 * The plugin is substituted to sbt projects, then you can run arbitrary command
 * (e.g., updateIntellij or packageArtifact) and run arbitrary assertions on the resulting artifacts
 *
 * The difference between org.jetbrains.sbtidea.packaging.MappingsTestBase is that the later test
 * runs assertions on pre-generated test data which has to be regenerated using RegenerateProjectsStructureTestData
 */
class SbtIdeaPluginIntegrationTest
  extends AnyFunSuite
    with Matchers
    with FileAssertions {

  private val TestProjectsDir = new File("ideaSupport/testData/projects").getAbsoluteFile
  private val IntellijSdksBaseDir = new File("tempIntellijSdks").getAbsoluteFile

  private lazy val PluginVersion = CurrentEnvironmentUtils.publishCurrentSbtIdeaPluginToLocalRepoAndGetVersions

  private val CommonIntellijBuild = "243.22562.145"

  private def doCommonAssertions(intellijSdkRoot: File): Unit = {
    assertFileExists(intellijSdkRoot)
    assertFileExists(intellijSdkRoot / "lib")
    assertFileExists(intellijSdkRoot / "plugins")
    assertFileExists(intellijSdkRoot / "product-info.json")
    assertFileExists(intellijSdkRoot / "sources" / "ideaIC-243.22562.145-sources.zip")
  }

  test("Simple project with plugin") {
    val intellijSdkRoot = runUpdateIntellijCommand("simple-with-plugin")

    doCommonAssertions(intellijSdkRoot)
    assertFileExists(intellijSdkRoot / "plugins" / "Scala")
    assertFileDoesNotExist(intellijSdkRoot / "sources" / "ideaIU-243.22562.145-sources.zip")
    new IdeInstallationContext(intellijSdkRoot.toPath).productInfo.productCode shouldBe "IC"
  }

  //NOTE: it seems like this test will only pass in JetBrains internal network and won't work on GitHub
  test("Simple project with Ultimate Edition") {
    val intellijSdkRoot = runUpdateIntellijCommand("simple-ultimate-edition")

    doCommonAssertions(intellijSdkRoot)
    assertFileExists(intellijSdkRoot / "sources" / "ideaIU-243.22562.145-sources.zip")

    assertFileDoesNotExist(intellijSdkRoot / "plugins" / "scala")
    new IdeInstallationContext(intellijSdkRoot.toPath).productInfo.productCode shouldBe "IU"
  }

  test("Project with library dependency with multiple artifacts") {
    val projectDir = TestProjectsDir / "dependency-with-multiple-artifacts"
    runUpdateIntellijCommand(projectDir)

    runSbtProcess(Seq("packageArtifact"), projectDir)

    val dumpedFileTree = dumpFileStructure(projectDir / "target" / "plugin")
    val expectedFileTree =
      """plugin/
        |  MyAwesomeFramework/
        |    lib/
        |      lwjgl-3.3.6-natives-linux.jar
        |      lwjgl-3.3.6-natives-macos-arm64.jar
        |      lwjgl-3.3.6-natives-macos.jar
        |      lwjgl-3.3.6-natives-windows-x86.jar
        |      lwjgl-3.3.6-natives-windows.jar
        |      lwjgl-3.3.6.jar
        |      lwjgl-jawt-3.3.5.jar
        |      lwjgl-opengl-3.3.6.jar
        |      lwjgl-vulkan-3.3.6-natives-macos-arm64.jar
        |      lwjgl-vulkan-3.3.6-natives-macos.jar
        |      lwjgl3-awt-0.2.3.jar
        |      myAwesomeFramework.jar
        |      scala-library-2.13.15.jar
        |""".stripMargin

    dumpedFileTree shouldBe expectedFileTree
  }

  private def dumpFileStructure(directory: File): String = {
    val IndentIncrement = "  "

    def inner(currentDir: File, currentIndent: String = "", builder: StringBuilder): Unit = {
      assert(currentDir.isDirectory, "Can only dump file structure for directories")
      builder.append(s"$currentIndent${currentDir.getName}/\n")

      val filesSorted = currentDir.listFiles.toSeq.sortBy(_.getName)
      filesSorted.foreach { file =>
        val childrenIndent = currentIndent + IndentIncrement
        if (file.isDirectory) {
          inner(file, childrenIndent, builder)
        } else {
          builder.append(s"$childrenIndent${file.getName}\n")
        }
      }
    }

    val builder = new StringBuilder
    inner(directory, builder = builder)
    builder.toString
  }

  private def runUpdateIntellijCommand(testProjectDirName: String): File = {
    runUpdateIntellijCommand(TestProjectsDir / testProjectDirName)
  }

  private def runUpdateIntellijCommand(projectDir: File): File = {
    assertFileExists(projectDir)

    SbtProjectFilesUtils.cleanUntrackedVcsFiles(projectDir)
    SbtProjectFilesUtils.updateSbtIdeaPluginToVersion(projectDir, PluginVersion)

    val intellijSdkRoot = SbtProjectFilesUtils.injectExtraSbtFileWithIntelliJSdkTargetDirSettings(projectDir, IntellijSdksBaseDir)

    runSbtProcess(
      Seq("updateIntellij"),
      projectDir,
      //ensure we reuse downloaded artifacts between tests if they need the same artifacts
      vmOptions = Seq("-Dsbt.idea.plugin.keep.downloaded.files=true"),
    )

    intellijSdkRoot / "sdk" / CommonIntellijBuild
  }
}