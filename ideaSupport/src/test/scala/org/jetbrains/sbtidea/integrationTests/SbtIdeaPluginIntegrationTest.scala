package org.jetbrains.sbtidea.integrationTests

import org.jetbrains.sbtidea.download.api.IdeInstallationContext
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils.IoMode.PrintAndCollectOutput
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils.runSbtProcess
import org.jetbrains.sbtidea.testUtils.IoUtils
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
  extends SbtIdeaPluginIntegrationTestBase
    with Matchers {

  private val ProvidedModuleNamesCacheDebugProperty = "sbtidea.providedModuleNamesCache.debug"
  private val ProvidedModuleNamesCalculationMarker = "[sbt-idea-plugin] calculating provided module names for "

  private def doCommonAssertions(intellijSdkRoot: File): Unit = {
    assertFileExists(intellijSdkRoot)
    assertFileExists(intellijSdkRoot / "lib")
    assertFileExists(intellijSdkRoot / "modules" / "module-descriptors.jar")
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
    val projectDir = testProjectsDir / "dependency-with-multiple-artifacts"
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
    val projectDir = testProjectsDir / "simple-with-plugin"

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

  test("intellijExtraJUnitTemplateLibraryDependencies appears only on the JUnit template classpath") {
    val projectDir = testProjectsDir / "simple-with-plugin"

    // Inject SDK paths/settings into the fixture; this rewrites extra.sbt from a clean state
    // (runUpdateIntellijCommand calls cleanUntrackedVcsFiles -> git clean -fdx first).
    runUpdateIntellijCommand(projectDir)

    // Configure one extra dep that should appear ONLY on the generated JUnit template classpath.
    appendExtraJUnitTemplateLibDepToExtraSbt(projectDir)

    // 1) Generate the JUnit template — the jar must end up in the argfile.
    runSbtProcess(Seq("createIDEARunConfiguration"), projectDir)

    val argFile = projectDir / ".idea" / "runConfigurations" / "junit_template_argfile.txt"
    assertFileExists(argFile)
    val argFileText = IoUtils.readLines(argFile).mkString(System.lineSeparator())
    argFileText should include("commons-io-2.16.1.jar")

    // 2) The same jar must NOT appear on the sbt Test / fullClasspath.
    val result = runSbtProcess(
      Seq("show Test/fullClasspath"),
      projectDir,
      ioMode = SbtProjectFilesUtils.IoMode.PrintAndCollectOutput,
    )
    val fullClasspathOutput = result.outputLines.getOrElse(Seq.empty).mkString("\n")
    fullClasspathOutput should not include "commons-io-2.16.1.jar"
  }

  private def appendExtraJUnitTemplateLibDepToExtraSbt(projectDir: File): Unit = {
    val extraSbt = projectDir / "extra.sbt"
    assertFileExists(extraSbt)
    val current = IoUtils.readLines(extraSbt).mkString(System.lineSeparator())
    //language=SBT
    val additional =
      """
        |
        |intellijExtraJUnitTemplateLibraryDependencies += "commons-io" % "commons-io" % "2.16.1"
        |""".stripMargin
    IoUtils.writeStringToFile(extraSbt, current + additional)
  }

  test("provided module names cache is reused across intellijPluginJars calculations") {
    val projectDir = testProjectsDir / "multi-module-plugin-jars-cache"
    runUpdateIntellijCommand(projectDir)

    val outputLines = runSbtProcess(
      sbtArguments = Seq(
        "show moduleA / intellijPluginJars",
        "show moduleB / intellijPluginJars",
        "show moduleC / intellijPluginJars",
        "show moduleA / intellijPluginJars",
      ),
      workingDir = projectDir,
      ioMode = PrintAndCollectOutput,
      vmOptions = Seq(s"-D$ProvidedModuleNamesCacheDebugProperty=true"),
    ).outputLines.get

    val calculationLines = outputLines.filter(_.contains(ProvidedModuleNamesCalculationMarker))
    withClue(
      s"""Expected exactly one provided-module-names calculation in one sbt JVM across repeated intellijPluginJars lookups.
         |Actual marker count: ${calculationLines.size}
         |Matching lines:
         |${calculationLines.mkString("\n")}""".stripMargin
    ) {
      calculationLines.size shouldBe 1
    }
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
}
