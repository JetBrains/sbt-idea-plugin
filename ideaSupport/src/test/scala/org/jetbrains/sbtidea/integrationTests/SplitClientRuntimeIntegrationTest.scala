package org.jetbrains.sbtidea.integrationTests

import org.jetbrains.sbtidea.CheckoutRuntimeDirectories
import org.jetbrains.sbtidea.productInfo.ProductInfoParser
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils.{runProcess, runSbtProcess}
import org.scalatest.matchers.should.Matchers
import sbt.{File, IO, fileToRichFile}

import java.nio.file.Paths
import java.util.Properties
import scala.xml.XML

class SplitClientRuntimeIntegrationTest extends SbtIdeaPluginIntegrationTestBase with Matchers {
  for (useNew <- Seq(true, false)) {
    test(s"split frontend loads isolated, shared and overridden paths from generated ${if (useNew) "current" else "legacy"} configuration") {
      // Reuse the normal SDK fixture, but keep every runtime created by this probe temporary.
      val prepared = runUpdateIntellijCommandInTemporaryProject("simple-with-plugin")
      val project = prepared.projectDir
      val sdk = prepared.intellijSdkRoot
      val home = newTmpDir.toFile / "plugin home with spaces"
      val info = ProductInfoParser.parse(sdk / "product-info.json")
      val isolated = CheckoutRuntimeDirectories(home.toPath, project).devClient(s"${info.productCode}-${info.buildNumber}")
      val explicitSystem = home / "explicit client system"
      val cases = Seq(
        ("isolated", false, None, isolated.system.toFile, isolated.config.toFile),
        ("shared", true, None, home / "runtime" / "system-client-shared", home / "runtime" / "config-client-shared"),
        ("shared with explicit system", true, Some(explicitSystem), explicitSystem, home / "runtime" / "config-client-shared"),
      )

      cases.foreach { case (label, shared, overrideSystem, expectedSystem, expectedConfig) =>
        withClue(s"$label: ") {
          IO.write(project / "extra.sbt", s"""
            |import org.jetbrains.sbtidea.Keys._
            |Global / onLoad := identity
            |name := "root"
            |ThisBuild / scalaVersion := "2.12.21"
            |ThisBuild / intellijPluginDirectory := file(${quote(home)})
            |ThisBuild / intellijBaseDirectory := file(${quote(sdk)})
            |useNewVmOptions := $useNew
            |customIntellijVMOptions := customIntellijVMOptions.value.withExtraOption("-Dfrontend.builder.marker=current")
            |intellijVMOptions := intellijVMOptions.value.withOptions(Seq("-Dfrontend.builder.marker=legacy"))
            |ThisBuild / intellijDevUseSharedDirectories := $shared
            |ideaConfigOptions := IdeaConfigBuildingOptions(generateDefaultRunConfig = false,
            |  generateJUnitTemplate = false, generateRunConfigForSplitMode = true)
            |${overrideSystem.map(path => s"ThisBuild / intellijDevClientSystemDir := file(${quote(path)})").getOrElse("")}
            |""".stripMargin)
          runSbtProcess(Seq("createIDEARunConfiguration"), project)

          val configs = project / ".idea" / "runConfigurations"
          val xml = XML.loadFile(configs / "root (split mode).xml")
          val environment = (xml \\ "env").map(node => (node \ "@name").text -> (node \ "@value").text).toMap
          val clientProperties = new File(environment("JETBRAINS_CLIENT_PROPERTIES"))
          clientProperties.getCanonicalFile shouldBe (configs / "idea_client.properties").getCanonicalFile
          val jre = (xml \\ "option").find(node => (node \ "@name").text == "ALTERNATIVE_JRE_PATH").get
          environment("JETBRAINS_CLIENT_JDK") shouldBe (jre \ "@value").text

          val classes = Paths.get(classOf[SplitClientPathProbe].getProtectionDomain.getCodeSource.getLocation.toURI).toFile
          val classpath = Seq(classes, sdk / "lib" / "*").map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)
          val output = project / "client-observed.properties"
          // Use the generated backend's environment hand-off, not manually supplied directory -D options.
          // This covers real PathManager adoption; a complete frontend/backend UI connection is separate.
          runProcess(Seq((new File(environment("JETBRAINS_CLIENT_JDK")) / "bin" / "java").getAbsolutePath,
            "-Djava.awt.headless=true", s"-Didea.home.path=${sdk.getAbsolutePath}",
            s"-Duser.home=${newTmpDir}", s"-Didea.properties.file=${clientProperties.getAbsolutePath}",
            "-cp", classpath, classOf[SplitClientPathProbe].getName, output.getAbsolutePath), project)
          val observed = readProperties(output)
          val generated = readProperties(clientProperties)
          Seq("idea.system.path", "idea.config.path", "idea.log.path", "plugin.path").foreach { key =>
            observed.getProperty(key) shouldBe generated.getProperty(key)
          }
          Seq("idea.system.path" -> expectedSystem, "idea.config.path" -> expectedConfig,
            "idea.log.path" -> (expectedSystem / "log"), "resolved.system" -> expectedSystem,
            "resolved.config" -> expectedConfig, "resolved.log" -> (expectedSystem / "log")).foreach { case (key, path) =>
            new File(observed.getProperty(key)).getCanonicalFile shouldBe path.getCanonicalFile
          }
          observed.getProperty("plugin.path") should not be empty
          // Frontend selection must remain separate from the backend, including shared mode.
          val backendVmOptions = (xml \\ "option").find(node => (node \ "@name").text == "VM_PARAMETERS").get
          (backendVmOptions \ "@value").text should not include expectedSystem.getAbsolutePath
          (backendVmOptions \ "@value").text should include(s"-Dfrontend.builder.marker=${if (useNew) "current" else "legacy"}")
        }
      }
    }
  }

  private def readProperties(file: File): Properties = {
    val properties = new Properties
    val in = java.nio.file.Files.newInputStream(file.toPath)
    try properties.load(in) finally in.close()
    properties
  }

  private def quote(file: File): String =
    "\"" + file.getAbsolutePath.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
