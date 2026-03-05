package org.jetbrains.sbtidea.integrationTests

import org.apache.commons.io.FileUtils
import org.jetbrains.sbtidea.download.api.IdeInstallationContext
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils.runSbtProcess
import org.jetbrains.sbtidea.testUtils.{CurrentEnvironmentUtils, FileAssertions, IoUtils, SbtProjectFilesUtils}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sbt.{File, fileToRichFile}

/**
 * This test is designed to test the work of sbt-idea-plugin as a whole, in real projects.
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
    assertFileExists(intellijSdkRoot / ".toolbox-ignore")
  }

  /**
   * Verifies the contents of the sources directory against an expected list of files
   *
   * @param intellijSdkRoot The root directory of the IntelliJ SDK
   * @param expectedSourcesFiles A sequence of expected sources filenames
   */
  private def assertSourcesDirectoryContents(intellijSdkRoot: File, expectedSourcesFiles: Seq[String]): Unit = {
    val sourcesDir = intellijSdkRoot / "sources"
    assertFileExists(sourcesDir)
    assertDirectoryContents(sourcesDir, expectedSourcesFiles)
  }

  test("Simple project with plugin") {
    val intellijSdkRoot = runUpdateIntellijCommand("simple-with-plugin")

    doCommonAssertions(intellijSdkRoot)
    assertFileDoesNotExist(intellijSdkRoot / "plugins" / "Scala")
    assertFileExists(intellijSdkRoot / "custom-plugins")
    assertFileExists(intellijSdkRoot / "custom-plugins" / "Scala")
    assertSourcesDirectoryContents(intellijSdkRoot, Seq(
      "ideaIC-243.22562.145-sources.zip"
    ))
    new IdeInstallationContext(intellijSdkRoot.toPath).productInfo.productCode shouldBe "IC"
  }

  //NOTE: it seems like this test will only pass in JetBrains internal network and won't work on GitHub
  test("Simple project with Ultimate Edition") {
    val intellijSdkRoot = runUpdateIntellijCommand("simple-ultimate-edition")

    doCommonAssertions(intellijSdkRoot)
    assertSourcesDirectoryContents(intellijSdkRoot, Seq(
      "ideaIU-243.22562.145-sources.zip"
    ))
    assertFileDoesNotExist(intellijSdkRoot / "plugins" / "scala")
    assertFileDoesNotExist(intellijSdkRoot / "custom-plugins")
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

  test("createIDEARunConfiguration uses Test/customIntellijVMOptions for JUnit template") {
    val projectDir = TestProjectsDir / "simple-with-plugin"

    // Ensure SDK paths/settings are injected into the fixture before generating run configs.
    runUpdateIntellijCommand(projectDir)

    // Add different VM option markers in Compile vs Test scopes to verify scope selection.
    appendVmOptionsScopeMarkersToExtraSbt(projectDir)

    // Generate IntelliJ run configurations including the JUnit template and its argfile.
    runSbtProcess(Seq("createIDEARunConfiguration"), projectDir)

    val runConfigurationsDir = projectDir / ".idea" / "runConfigurations"
    val argFile = runConfigurationsDir / "junit_template_argfile.txt"
    assertFileExists(argFile)

    // The generated JUnit logfile must use Test-scoped VM options, not Compile-scoped ones.
    val argFileText = IoUtils.readLines(argFile).mkString(System.lineSeparator())
    argFileText should include("-Dscope.marker=test")
    argFileText should include("-Dscope.marker=compile1")
    argFileText should not include "-Dscope.marker=compile2"

    // The template must reference the argfile that actually carries the VM options.
    val junitTemplateFile = runConfigurationsDir / "_template__of_JUnit.xml"
    assertFileExists(junitTemplateFile)
    val junitTemplateText = IoUtils.readLines(junitTemplateFile).mkString(System.lineSeparator())
    junitTemplateText should include("junit_template_argfile.txt")
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

  private def appendVmOptionsScopeMarkersToExtraSbt(projectDir: File): Unit = {
    val extraSbt = projectDir / "extra.sbt"
    assertFileExists(extraSbt)

    val currentContent = IoUtils.readLines(extraSbt).mkString(System.lineSeparator())

    //language=SBT
    val additionalSettings =
      """
        |
        |customIntellijVMOptions := (LocalRootProject / customIntellijVMOptions).value
        |  .withExtraOptions(Seq("-Dscope.marker=compile1"))
        |
        |Compile / customIntellijVMOptions := (Compile / customIntellijVMOptions).value
        |  .withExtraOptions(Seq("-Dscope.marker=compile2"))
        |
        |Test / customIntellijVMOptions := (Test / customIntellijVMOptions).value
        |  .withExtraOptions(Seq("-Dscope.marker=test"))
        |""".stripMargin

    IoUtils.writeStringToFile(extraSbt, currentContent + additionalSettings)
  }

  private def runUpdateIntellijCommand(projectDir: File): File = {
    assertFileExists(projectDir)

    val sdkRoot = IntellijSdksBaseDir / projectDir.getName

    // Ensure we have a clean SDK directory
    FileUtils.deleteDirectory(sdkRoot)

    SbtProjectFilesUtils.cleanUntrackedVcsFiles(projectDir)
    SbtProjectFilesUtils.updateSbtIdeaPluginToVersion(projectDir, PluginVersion)


    val intellijSdkRoot = SbtProjectFilesUtils.injectExtraSbtFileWithIntelliJSdkTargetDirSettingsForSdkRoot(projectDir, sdkRoot)

    runSbtProcess(
      Seq("updateIntellij"),
      projectDir,
      //ensure we reuse downloaded artifacts between tests if they need the same artifacts
      vmOptions = Seq("-Dsbt.idea.plugin.keep.downloaded.files=true"),
    )

    intellijSdkRoot / "sdk" / CommonIntellijBuild
  }
}
