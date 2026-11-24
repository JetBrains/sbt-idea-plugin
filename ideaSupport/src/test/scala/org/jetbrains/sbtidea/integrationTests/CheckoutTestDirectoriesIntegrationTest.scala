package org.jetbrains.sbtidea.integrationTests

import org.jetbrains.sbtidea.CheckoutRuntimeDirectories
import org.jetbrains.sbtidea.{CapturingLogger, runtimeCleanup}
import org.jetbrains.sbtidea.download.jbr.JbrInstaller
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils.{runProcess, runSbtProcess}
import org.scalatest.matchers.should.Matchers
import sbt.{File, IO, fileToRichFile}

import java.nio.file.Files
import java.time.Instant
import java.util.Properties
import java.util.concurrent.Executors
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.xml.XML
import scala.util.Try

class CheckoutTestDirectoriesIntegrationTest extends SbtIdeaPluginIntegrationTestBase with Matchers {
  private val buildId = "IU-243.22562.145"
  private val directoryKeys = Seq("idea.system.path", "idea.config.path", "idea.log.path")

  test("test preparation logs final JVM paths once while directory inspection stays quiet") {
    val home = newTmpDir.toFile / "plugin home with spaces"
    val project = prepareConsumer(home)
    val inspected = runSbtProcess(Seq("dumpPaths"), project,
      ioMode = SbtProjectFilesUtils.IoMode.PrintAndCollectOutput).outputLines
      .getOrElse(fail(s"No captured directory-inspection output for $project")).mkString("\n")
    inspected should not include "IntelliJ runtime ("
    assertFileDoesNotExist(CheckoutRuntimeDirectories(home.toPath, project).tests(buildId).system.toFile)

    val configuredSystem = home / "configured system"
    val config = home / "explicit config"
    val finalSystem = home / "final JVM system"
    val finalLog = home / "final JVM logs"
    IO.append(project / "extra.sbt", s"""
      |ThisBuild / intellijDevUseSharedDirectories := true
      |ThisBuild / intellijRuntimeBuildId := sys.error("logging must not resolve SDK identity")
      |ThisBuild / intellijTestSystemDir := file(${quote(configuredSystem)})
      |ThisBuild / intellijTestConfigDir := file(${quote(config)})
      |Test / javaOptions ++= Seq("-Didea.system.path=" + ${quote(finalSystem)}, "-Didea.log.path=" + ${quote(finalLog)})
      |""".stripMargin)
    val lines = runSbtProcess(Seq("probe"), project,
      ioMode = SbtProjectFilesUtils.IoMode.PrintAndCollectOutput).outputLines
      .getOrElse(fail(s"No captured test-fork output for $project"))
    val output = lines.mkString("\n")
    withClue("One test-fork preparation must print one summary: ") {
      lines.count(_.contains("IntelliJ runtime (tests, root)")) shouldBe 1
    }
    val actual = readProperties(project / "observed.properties")
    Seq("System" -> "idea.system.path", "Config" -> "idea.config.path", "Logs" -> "idea.log.path").foreach {
      case (label, key) => output should include(s"$label: ${actual.getProperty(key)}")
    }
    assertProperty(actual, "idea.system.path", finalSystem.getAbsolutePath, "Summary must reflect final javaOptions")
    assertProperty(actual, "idea.config.path", config.getAbsolutePath, "Independent config override must be retained")
    assertProperty(actual, "idea.log.path", finalLog.getAbsolutePath, "Independent final log override must be retained")
    assertFileDoesNotExist(configuredSystem)
    Seq(config, finalSystem, finalLog).foreach(assertFileExists)
  }

  test("independent checkouts share caches but use distinct test directories in concurrent forked JVMs and JUnit") {
    val home = newTmpDir.toFile / "shared plugin home"
    val sentinels = Seq("test-system", "test-config", "system", "config", "sdk/downloads", "sdk/243.22562.145/custom-plugins",
      "runtime/IU-243.22562.145/unrelated/tests/system", "runtime/system-shared", "runtime/config-shared")
      .map(path => home / path / "keep.txt")
    sentinels.foreach(IO.write(_, "keep"))
    val first = prepareConsumer(home)
    val second = prepareConsumer(home)
    val firstDirs = CheckoutRuntimeDirectories(home.toPath, first).tests(buildId)
    val secondDirs = CheckoutRuntimeDirectories(home.toPath, second).tests(buildId)
    // Preserve data from the earlier, unreleased checkout-first layout as well.
    val previousRoot = home / "runtime" / CheckoutRuntimeDirectories(home.toPath, first).checkoutHash
    val previousSentinels = Seq("tests", "dev").flatMap { purpose =>
      Seq("system", "config").map(previousRoot / s"$buildId-$purpose" / _ / "keep.txt")
    }
    previousSentinels.foreach(IO.write(_, "keep"))
    val otherBuild = CheckoutRuntimeDirectories(home.toPath, first).tests("IU-243.22562.146")
    IO.write(otherBuild.system.resolve("keep.txt").toFile, "keep")
    withClue(s"Independent checkouts $first and $second must use distinct test system directories: ") {
      firstDirs.system should not be secondDirs.system
    }

    runSbtProcess(Seq("dumpPaths", "child / dumpPaths"), first)
    val expected = readProperties(first / "settings.properties")
    directoryKeys.foreach { key =>
      assertProperty(readProperties(first / "child" / "settings.properties"), key, expected.getProperty(key),
        s"Subproject of $first must inherit the root test directories")
    }
    assertFileDoesNotExist(firstDirs.system.toFile)
    assertFileDoesNotExist(firstDirs.config.toFile)

    IO.write(first / "peer.txt", (second / "observed.properties").getAbsolutePath)
    IO.write(second / "peer.txt", (first / "observed.properties").getAbsolutePath)
    val executor = Executors.newFixedThreadPool(2)
    implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(executor)
    try {
      val processes = Seq(first, second).map { project =>
        val commands = if (project == first)
          Seq("createIDEARunConfiguration", "assertMarkers", "probe", "dumpPaths")
        else Seq("probe", "assertMarkers", "createIDEARunConfiguration", "dumpPaths")
        Future(Try(runSbtProcess(commands, project)))
      }
      Await.result(Future.sequence(processes), 5.minutes).foreach(_.get)
    } finally executor.shutdown()

    Seq(first, second).foreach { project =>
      assertProbeAndTemplateAgree(project, home)
      val dirs = CheckoutRuntimeDirectories(home.toPath, project).tests(buildId)
      Seq(dirs.system, dirs.config).foreach { dir =>
        val readme = dir.resolve("README.txt").toFile
        withClue(s"Runtime marker $readme must identify its owning checkout: ") {
          IO.read(readme) should include(project.getCanonicalPath)
        }
      }
      val readme = dirs.system.resolve("README.txt")
      val timestamp = Files.getLastModifiedTime(readme)
      runSbtProcess(Seq("createIDEARunConfiguration"), project)
      withClue(s"Regenerating configurations must not overwrite runtime marker $readme: ") {
        Files.getLastModifiedTime(readme) shouldBe timestamp
      }
    }

    runSbtProcess(Seq("cleanUpTestEnvironment"), first)
    withClue(s"cleanUpTestEnvironment in $first must delete only its selected test directories: ") {
      assertFileDoesNotExist(firstDirs.system.toFile)
      assertFileDoesNotExist(firstDirs.config.toFile)
      assertFileExists(secondDirs.system.resolve("README.txt").toFile)
      assertFileExists(secondDirs.config.resolve("README.txt").toFile)
    }
    sentinels.foreach(assertFileContent(_, "keep"))
    previousSentinels.foreach(assertFileContent(_, "keep"))
    assertFileContent(otherBuild.system.resolve("keep.txt").toFile, "keep")
  }

  test("scoped directory overrides, JUnit generation and cleanup use the same selected paths") {
    val home = newTmpDir.toFile / "plugin home"
    val project = prepareConsumer(home)
    val system = home / "explicit system"
    val config = home / "explicit config"
    val defaults = CheckoutRuntimeDirectories(home.toPath, project).tests(buildId)
    IO.write(defaults.system.resolve("keep.txt").toFile, "keep")
    IO.write(defaults.config.resolve("keep.txt").toFile, "keep")
    IO.append(project / "extra.sbt",
      s"""
         |LocalRootProject / Test / intellijTestSystemDir := file(${quote(system)})
         |ThisBuild / intellijTestConfigDir := file(${quote(config)})
         |""".stripMargin)

    runSbtProcess(Seq("probe", "dumpPaths", "createIDEARunConfiguration"), project)
    val properties = readProperties(project / "settings.properties")
    assertProperty(properties, "idea.system.path", system.getAbsolutePath, s"Explicit test system override in $project")
    assertProperty(properties, "idea.config.path", config.getAbsolutePath, s"Explicit test config override in $project")
    assertProbeAndTemplateAgree(project, home)
    assertFileDoesNotExist(system / "README.txt")
    assertFileDoesNotExist(config / "README.txt")
    assertFileDoesNotExist(defaults.system.resolve("README.txt").toFile)
    assertFileDoesNotExist(defaults.config.resolve("README.txt").toFile)

    runSbtProcess(Seq("cleanUpTestEnvironment"), project)
    assertFileDoesNotExist(system)
    assertFileDoesNotExist(config)
    assertFileContent(defaults.system.resolve("keep.txt").toFile, "keep")
    assertFileContent(defaults.config.resolve("keep.txt").toFile, "keep")
  }

  test("sharing property spans builds and checkouts, while explicit flag assignments win") {
    val home = newTmpDir.toFile / "shared home"
    val first = prepareConsumer(home)
    val second = prepareConsumer(home)
    val secondBuild = "243.22562.146"
    // The requested label and SDK directory are deliberately different from the reported build.
    IO.append(second / "extra.sbt",
      s"""
         |ThisBuild / intellijBuild := "LATEST-EAP-SNAPSHOT"
         |ThisBuild / intellijBaseDirectory := file(${quote(home / "sdk" / "243.22562.145")})
         |""".stripMargin)
    val commands = Seq("dumpPaths", "createIDEARunConfiguration", "probe")
    runSbtProcess(commands, first, vmOptions = Seq("-Dsbtidea.dev.sharedDirectories=true"))
    runSbtProcess(commands, second, vmOptions = Seq("-Dsbtidea.dev.sharedDirectories=true", s"-DdirectoryProbe.build=$secondBuild"))
    val firstPaths = readProperties(first / "settings.properties")
    val secondPaths = readProperties(second / "settings.properties")
    val shared = Map("dev.system" -> "system-shared", "dev.config" -> "config-shared",
      "client.system" -> "system-client-shared", "client.config" -> "config-client-shared")
    shared.foreach { case (key, name) =>
      assertProperty(firstPaths, key, (home / "runtime" / name).getAbsolutePath, s"Shared development paths in $first")
      assertProperty(secondPaths, key, firstPaths.getProperty(key), s"Shared development paths in $second")
    }
    Seq("idea.system.path", "idea.config.path", "index.system", "index.config").foreach { key =>
      withClue(s"Sharing development directories must leave '$key' isolated between $first and $second: ") {
        firstPaths.getProperty(key) should not be secondPaths.getProperty(key)
      }
    }
    assertProperty(secondPaths, "idea.system.path",
      (home / "runtime" / s"IU-$secondBuild" / CheckoutRuntimeDirectories(home.toPath, second).checkoutHash /
        "tests" / "system").getAbsolutePath, s"Installed SDK build must select the test path in $second")
    Seq(first, second).foreach(assertProbeAndTemplateAgree(_, home))

    runSbtProcess(Seq("set ThisBuild / intellijDevUseSharedDirectories := false") ++ commands,
      first, vmOptions = Seq("-Dsbtidea.dev.sharedDirectories=true"))
    val isolated = readProperties(first / "settings.properties")
    val isolatedContext = s"Explicit sharing=false must override the JVM property in $first"
    assertProperty(isolated, "sharing", "false", isolatedContext)
    assertProperty(isolated, "dev.system", CheckoutRuntimeDirectories(home.toPath, first).dev(buildId).system.toString, isolatedContext)
    assertProperty(isolated, "client.config", CheckoutRuntimeDirectories(home.toPath, first).devClient(buildId).config.toString, isolatedContext)
    assertProbeAndTemplateAgree(first, home)

    val customDevSystem = home / "selected dev system"
    runSbtProcess(Seq("set ThisBuild / intellijDevUseSharedDirectories := true",
      s"set ThisBuild / intellijDevSystemDir := file(${quote(customDevSystem)})", "dumpPaths"),
      second, vmOptions = Seq("-Dsbtidea.dev.sharedDirectories=false", s"-DdirectoryProbe.build=$secondBuild"))
    val mixed = readProperties(second / "settings.properties")
    val mixedContext = s"Explicit directory overrides must coexist with sharing=true in $second"
    assertProperty(mixed, "dev.config", firstPaths.getProperty("dev.config"), mixedContext)
    assertProperty(mixed, "dev.system", customDevSystem.getAbsolutePath, mixedContext)
    assertProperty(mixed, "client.system", firstPaths.getProperty("client.system"), mixedContext)
    // Configuration generation must not initialize development config directories.
    assertFileDoesNotExist(home / "runtime" / "config-shared")
  }

  test("an explicit build ID selects every isolated purpose and bypasses missing SDK metadata for path inspection") {
    val home = newTmpDir.toFile / "home"
    val project = prepareConsumer(home)
    val customId = "IU-243.22562.145-custom-2"
    IO.append(project / "extra.sbt", s"""
      |ThisBuild / intellijRuntimeBuildId := "$customId"
      |""".stripMargin)
    runSbtProcess(Seq("dumpPaths", "createIDEARunConfiguration", "probe"), project)
    assertProbeAndTemplateAgree(project, home)
    val paths = readProperties(project / "settings.properties")
    val resolver = CheckoutRuntimeDirectories(home.toPath, project)
    val context = s"Custom runtime build ID $customId in $project"
    assertProperty(paths, "idea.system.path", resolver.tests(customId).system.toString, context)
    assertProperty(paths, "dev.system", resolver.dev(customId).system.toString, context)
    assertProperty(paths, "client.config", resolver.devClient(customId).config.toString, context)
    assertProperty(paths, "index.config", resolver.buildIndex(customId).config.toString, context)
    val readme = resolver.tests(customId).system.resolve("README.txt").toFile
    withClue(s"Runtime marker $readme must identify custom build $customId: ") {
      IO.read(readme) should include(customId)
    }

    runSbtProcess(Seq("set ThisBuild / productInfo := sys.error(\"unused SDK metadata evaluated\")",
      "show Test / intellijTestConfigDir", "show intellijDevSystemDir", "show intellijDevClientConfigDir",
      "set ThisBuild / intellijRuntimeBuildId := sys.error(\"unused identity evaluated\")",
      "set ThisBuild / intellijDevUseSharedDirectories := true",
      "show intellijDevSystemDir", "show intellijDevConfigDir",
      "show intellijDevClientSystemDir", "show intellijDevClientConfigDir"), project)
    assertFileDoesNotExist(resolver.dev(customId).system.toFile)
    assertFileDoesNotExist(resolver.buildIndex(customId).system.toFile)
  }

  test("exact paths override sharing and need no runtime identity for lookup, generation, preparation or cleanup") {
    val home = newTmpDir.toFile / "home"
    val project = prepareConsumer(home)
    val paths = Seq("test system", "test config", "dev system", "dev config", "client system", "client config")
      .map(home / _)
    val keys = Seq("intellijTestSystemDir", "intellijTestConfigDir", "intellijDevSystemDir",
      "intellijDevConfigDir", "intellijDevClientSystemDir", "intellijDevClientConfigDir")
    IO.append(project / "extra.sbt", "\n" + keys.zip(paths).map { case (key, path) =>
      s"ThisBuild / $key := file(${quote(path)})"
    }.mkString("\n") + "\nThisBuild / intellijRuntimeBuildId := sys.error(\"unused identity evaluated\")\n")
    paths.drop(2).foreach(path => IO.write(path / "keep.txt", "keep"))
    runSbtProcess(Seq("show Test / intellijTestSystemDir", "createIDEARunConfiguration", "probe", "cleanUpTestEnvironment"),
      project, vmOptions = Seq("-Dsbtidea.dev.sharedDirectories=true"))
    val actual = readProperties(project / "observed.properties")
    assertProperty(actual, "idea.system.path", paths(0).getAbsolutePath, s"Explicit test system override in $project")
    assertProperty(actual, "idea.config.path", paths(1).getAbsolutePath, s"Explicit test config override in $project")
    paths.take(2).foreach(assertFileDoesNotExist)
    paths.drop(2).foreach(path => assertFileContent(path / "keep.txt", "keep"))
    paths.foreach(path => assertFileDoesNotExist(path / "README.txt"))
    val configs = project / ".idea" / "runConfigurations"
    val xml = IO.read(configs / "root.xml")
    withClue(s"Development configuration ${configs / "root.xml"} must use explicit backend paths: ") {
      xml should include(paths(2).getAbsolutePath)
      xml should include(paths(3).getAbsolutePath)
    }
    val client = readProperties(configs / "idea_client.properties")
    assertProperty(client, "idea.system.path", paths(4).getAbsolutePath, s"Explicit frontend paths in $configs")
    assertProperty(client, "idea.config.path", paths(5).getAbsolutePath, s"Explicit frontend paths in $configs")

    // Even missing SDK metadata is irrelevant to cleanup when both test paths are explicit.
    runSbtProcess(Seq("set ThisBuild / productInfo := sys.error(\"unused SDK metadata evaluated\")",
      "show Test / intellijTestConfigDir", "cleanUpTestEnvironment"), project)
  }

  test("explicit paths inside the managed layout need no identity and receive no markers") {
    val home = newTmpDir.toFile / "home"
    val project = prepareConsumer(home)
    val resolver = CheckoutRuntimeDirectories(home.toPath, project)
    val pinned = resolver.tests("IU-pinned-old-build")
    // Completeness regression for an unusual edge case: ordinary SDK metadata normally hides
    // this dependency. Explicitly pinned paths must work even when they look plugin-managed.
    IO.append(project / "extra.sbt", s"""
      |ThisBuild / intellijDevUseSharedDirectories := true
      |ThisBuild / intellijTestSystemDir := file(${quote(pinned.system.toFile)})
      |ThisBuild / intellijTestConfigDir := file(${quote(pinned.config.toFile)})
      |ThisBuild / intellijRuntimeBuildId := sys.error("unused identity evaluated")
      |""".stripMargin)

    runSbtProcess(Seq("show Test / intellijTestSystemDir", "createIDEARunConfiguration", "probe"), project)
    val observed = readProperties(project / "observed.properties")
    assertProperty(observed, "idea.system.path", pinned.system.toString, s"Pinned test paths in $project")
    assertProperty(observed, "idea.config.path", pinned.config.toString, s"Pinned test paths in $project")
    Seq(pinned.system, pinned.config).foreach { dir =>
      assertFileDoesNotExist(dir.resolve("README.txt").toFile)
    }

    // A user override equal to a current default is still user-owned; path equality is not provenance.
    val current = resolver.tests(buildId)
    runSbtProcess(Seq(
      s"""set ThisBuild / intellijRuntimeBuildId := "$buildId" """,
      s"set Test / intellijTestSystemDir := file(${quote(current.system.toFile)})",
      s"set Test / intellijTestConfigDir := file(${quote(current.config.toFile)})",
      "createIDEARunConfiguration", "probe"), project)
    Seq(current.system, current.config).foreach { dir =>
      assertFileDoesNotExist(dir.resolve("README.txt").toFile)
    }
  }

  test("an explicit system path equal to the default leaves only the default config managed") {
    val home = newTmpDir.toFile / "home"
    val project = prepareConsumer(home)
    val directories = CheckoutRuntimeDirectories(home.toPath, project).tests(buildId)
    IO.append(project / "extra.sbt", s"""
      |ThisBuild / intellijTestSystemDir := file(${quote(directories.system.toFile)})
      |""".stripMargin)

    runSbtProcess(Seq("createIDEARunConfiguration", "probe"), project)
    assertFileDoesNotExist(directories.system.resolve("README.txt").toFile)
    assertFileExists(directories.config.resolve("README.txt").toFile)
  }

  // Completeness regressions for unusual custom configurations: valid SDK metadata normally
  // hides these unused dependencies. Output generation must tolerate unavailable unused roles;
  // maintenance may inspect explicit overrides, but failure there only defers automatic cleanup.
  test("disabled split output does not require frontend directories") {
    val home = newTmpDir.toFile / "home"
    val project = prepareConsumer(home)
    IO.append(project / "extra.sbt", """
      |LocalRootProject / ideaConfigOptions := IdeaConfigBuildingOptions()
      |ThisBuild / intellijDevClientSystemDir := sys.error("disabled frontend system evaluated")
      |ThisBuild / intellijDevClientConfigDir := sys.error("disabled frontend config evaluated")
      |""".stripMargin)

    runSbtProcess(Seq("createIDEARunConfiguration", "assertMarkers"), project)
    val configs = project / ".idea" / "runConfigurations"
    val expected = CheckoutRuntimeDirectories(home.toPath, project).dev(buildId)
    withClue(s"Development configuration ${configs / "root.xml"} must use the backend system directory: ") {
      IO.read(configs / "root.xml") should include(expected.system.toString)
    }
    assert((configs / "_template__of_JUnit.xml").isFile, s"Missing generated JUnit template in $configs")
    assert((configs / "junit_template_argfile.txt").isFile, s"Missing generated JUnit argument file in $configs")
    assertFileDoesNotExist(configs / "root (split mode).xml")
    assertFileDoesNotExist(configs / "idea_client.properties")
  }

  test("disabled JUnit output does not resolve test directories for default, additional or split development configs") {
    Seq(
      "IdeaConfigBuildingOptions(generateJUnitTemplate = false)" -> "root.xml",
      "IdeaConfigBuildingOptions(generateDefaultRunConfig = false, generateJUnitTemplate = false, " +
        "additionalRunConfigs = Seq(org.jetbrains.sbtidea.IdeaConfigBuildingOptions.AdditionalRunConfigData(\" extra\", Nil)))" -> "root extra.xml",
      "IdeaConfigBuildingOptions(generateDefaultRunConfig = false, generateJUnitTemplate = false, " +
        "generateRunConfigForSplitMode = true)" -> "root (split mode).xml",
    ).foreach { case (options, output) =>
      val home = newTmpDir.toFile / "home"
      val project = prepareConsumer(home)
      IO.append(project / "extra.sbt", s"""
        |LocalRootProject / ideaConfigOptions := $options
        |ThisBuild / intellijDevUseSharedDirectories := true
        |ThisBuild / intellijRuntimeBuildId := sys.error("unused identity evaluated")
        |ThisBuild / intellijTestSystemDir := sys.error("disabled JUnit system evaluated")
        |ThisBuild / intellijTestConfigDir := sys.error("disabled JUnit config evaluated")
        |""".stripMargin)

      runSbtProcess(Seq("createIDEARunConfiguration"), project)
      val configs = project / ".idea" / "runConfigurations"
      withClue(s"Development configuration ${configs / output} with options $options must use shared directories: ") {
        IO.read(configs / output) should include((home / "runtime" / "system-shared").getAbsolutePath)
      }
      assertFileDoesNotExist(configs / "_template__of_JUnit.xml")
      assertFileDoesNotExist(configs / "junit_template_argfile.txt")
      assertFileDoesNotExist(CheckoutRuntimeDirectories(home.toPath, project).tests(buildId).system.toFile)
      assertFileDoesNotExist(CheckoutRuntimeDirectories(home.toPath, project).tests(buildId).config.toFile)
      if (output == "root (split mode).xml") {
        assertProperty(readProperties(configs / "idea_client.properties"), "idea.system.path",
          (home / "runtime" / "system-client-shared").getAbsolutePath, s"Shared frontend configuration in $configs")
      }
    }
  }

  test("JUnit-only output works with explicit test paths and unavailable development directories or build identity") {
    val home = newTmpDir.toFile / "home"
    val project = prepareConsumer(home)
    val system = home / "explicit test system"
    val config = home / "explicit test config"
    IO.append(project / "extra.sbt", s"""
      |LocalRootProject / ideaConfigOptions := IdeaConfigBuildingOptions(generateDefaultRunConfig = false)
      |ThisBuild / intellijTestSystemDir := file(${quote(system)})
      |ThisBuild / intellijTestConfigDir := file(${quote(config)})
      |ThisBuild / intellijRuntimeBuildId := sys.error("unused identity evaluated")
      |ThisBuild / intellijDevSystemDir := sys.error("disabled backend system evaluated")
      |ThisBuild / intellijDevConfigDir := sys.error("disabled backend config evaluated")
      |ThisBuild / intellijDevClientSystemDir := sys.error("disabled frontend system evaluated")
      |ThisBuild / intellijDevClientConfigDir := sys.error("disabled frontend config evaluated")
      |""".stripMargin)

    runSbtProcess(Seq("createIDEARunConfiguration"), project)
    val configs = project / ".idea" / "runConfigurations"
    assertDirectoryContents(configs, Seq("_template__of_JUnit.xml", "junit_template_argfile.txt"))
    val java = new File(System.getProperty("java.home")) / "bin" / "java"
    val observed = project / "junit-only.properties"
    runProcess(Seq(java.getAbsolutePath, "@" + (configs / "junit_template_argfile.txt").getAbsolutePath,
      "DirectoryProbe", observed.getAbsolutePath), project)
    val properties = readProperties(observed)
    assertProperty(properties, "idea.system.path", system.getAbsolutePath, s"JUnit-only JVM output $observed")
    assertProperty(properties, "idea.config.path", config.getAbsolutePath, s"JUnit-only JVM output $observed")
    assertProperty(properties, "idea.log.path", (system / "log").getAbsolutePath, s"JUnit-only JVM output $observed")
    Seq(system, config).foreach(path => assertFileDoesNotExist(path / "README.txt"))
  }

  test("disabling every configuration output is a no-op without SDK or runtime task evaluation") {
    val home = newTmpDir.toFile / "home"
    val project = prepareConsumer(home)
    IO.append(project / "extra.sbt", """
      |LocalRootProject / ideaConfigOptions := IdeaConfigBuildingOptions(
      |  generateDefaultRunConfig = false, generateJUnitTemplate = false)
      |ThisBuild / productInfo := sys.error("unused SDK metadata evaluated")
      |ThisBuild / intellijRuntimeBuildId := sys.error("unused identity evaluated")
      |ThisBuild / intellijDevSystemDir := sys.error("disabled backend evaluated")
      |ThisBuild / intellijDevClientSystemDir := sys.error("disabled frontend evaluated")
      |ThisBuild / intellijTestSystemDir := sys.error("disabled JUnit evaluated")
      |""".stripMargin)

    runSbtProcess(Seq("createIDEARunConfiguration"), project)
    assertFileDoesNotExist(project / ".idea" / "runConfigurations")
    assertFileDoesNotExist(home / "runtime")
  }

  test("automatic runtime maintenance runs on startup, cleans expired owned data, and shares its daily throttle") {
    val home = newTmpDir.toFile / "shared plugin home"
    val project = prepareConsumer(home)
    enableRuntimeMaintenanceStartup(project)
    val peer = prepareConsumer(home)
    enableRuntimeMaintenanceStartup(peer)
    IO.append(project / "extra.sbt", s"""
      |Global / onComplete := {
      |  val previous = (Global / onComplete).value
      |  () => { IO.write(file(${quote(project / "completion-hook.txt")}), "called"); previous() }
      |}
      |""".stripMargin)
    val oldCheckout = newTmpDir
    val oldResolver = CheckoutRuntimeDirectories(home.toPath, oldCheckout.toFile)
    val dirs = oldResolver.tests(buildId)
    val index = oldResolver.buildIndex(buildId)
    val paths = Map("test.system" -> dirs.system, "test.config" -> dirs.config,
      "index.system" -> index.system, "index.config" -> index.config)
    val oldTime = Instant.now().minusSeconds(9.days.toSeconds)
    val registry = new runtimeCleanup.RuntimeCleanup(home.toPath,
      runtimeCleanup.RuntimeCleanup.Policy(true, 7.days, 1.day), new CapturingLogger(), () => oldTime)
    // Warm sbt's shared global-plugin compilation cache before racing two consumer processes.
    // The test-owned checkpoint is then aged to make the competing cleanup passes due.
    runSbtProcess(Seq("show name"), project)
    IO.write(home / ".sbt-idea-plugin" / "runtime-cleanup-v1.properties",
      s"formatVersion=1\nlastAttempt=$oldTime\n")
    paths.values.foreach(path => IO.write(path.resolve("expired.txt").toFile, "expired"))
    registry.refresh(oldCheckout, buildId, paths)
    val keep = Seq(home / "test-system" / "keep", oldResolver.dev(buildId).system.resolve("keep").toFile,
      home / "sdk" / "downloads" / "keep", home / "runtime" / "unregistered" / "tests" / "system" / "keep")
    keep.foreach(IO.write(_, "keep"))

    val executor = Executors.newFixedThreadPool(2)
    implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(executor)
    try {
      val processes = Seq(project -> Seq("dumpPaths", "child / dumpPaths"), peer -> Seq("show name"))
        .map { case (consumer, commands) => Future(Try(runSbtProcess(commands, consumer))) }
      Await.result(Future.sequence(processes), 3.minutes).foreach(_.get)
    } finally executor.shutdown()
    paths.foreach { case (role, path) =>
      withClue(s"Due startup cleanup must remove expired '$role' for checkout $oldCheckout, build $buildId: ") {
        assertFileDoesNotExist(path.toFile)
      }
    }
    keep.foreach(assertFileContent(_, "keep"))
    val ownMarker = registry.marker(project.toPath, buildId).toFile
    assert(ownMarker.isFile, s"Startup must register the current checkout/build in usage marker $ownMarker")
    assertFileContent(project / "completion-hook.txt", "called")
    val record = readProperties(ownMarker)
    assertProperty(record, "retentionSeconds", "604800", s"Default cleanup policy in $ownMarker")
    assertProperty(record, "automaticCleanup", "true", s"Default cleanup policy in $ownMarker")
    val current = CheckoutRuntimeDirectories(home.toPath, project).tests(buildId)
    withClue("Startup must register usage without initializing runtime caches: ") {
      assertFileDoesNotExist(current.system.toFile)
    }

    paths.values.foreach(path => IO.write(path.resolve("expired.txt").toFile, "expired"))
    registry.refresh(oldCheckout, buildId, paths)
    runSbtProcess(Seq("show name"), project)
    paths.foreach { case (role, path) =>
      withClue(s"The shared daily throttle must preserve '$role' recreated after the preceding cleanup pass: ") {
        assertFileExists(path.resolve("expired.txt").toFile)
      }
    }
  }

  Seq(SbtProjectFilesUtils.SbtVersionForIntegrationTests, "1.4.5").foreach { sbtVersion =>
    test(s"startup runtime maintenance initializes non-aggregated plugin children with a plain root on sbt $sbtVersion") {
      val home = newTmpDir.toFile / "plugin home with spaces"
      val project = prepareConsumer(home)
      enableRuntimeMaintenanceStartup(project)
      SbtProjectFilesUtils.updateSbtVersion(project, sbtVersion)
      val build = project / "build.sbt"
      val plainRoot = "lazy val root = project.in(file(\".\"))"
      IO.write(build, IO.read(build).replace(
        plainRoot + ".enablePlugins(SbtIdeaPlugin).settings(commonSettings)", plainRoot + s""".settings(
        |  TaskKey[Unit]("clearStartupUsage") := {
        |    val markers = (file(${quote(home)}) / ".sbt-idea-plugin" / "runtime-usage-v1" ** "*.properties").get
        |    require(markers.size == 2, "Both plugin children must have initialized on load; expected 2 usage records, found " + markers)
        |    IO.delete(markers)
        |    require(markers.forall(!_.exists()), "Test-owned usage records should be absent before reload; remaining: " + markers.filter(_.exists()))
        |  }
        |)""".stripMargin) + """
        |lazy val other = project.in(file("other")).enablePlugins(SbtIdeaPlugin).settings(commonSettings).settings(
        |  intellijRuntimeBuildId := "IU-other-child"
        |)
        |""".stripMargin)
      // Exercise both children; neither is aggregated by the root. Startup must find
      // enabled projects itself, rather than relying on command resolution/aggregation.
      val vmOptions = Seq(s"-Dsbt.global.base=${newTmpDir.resolve("global")}")
      runSbtProcess(Seq("show name"), project, vmOptions = vmOptions)
      val registry = new runtimeCleanup.RuntimeCleanup(home.toPath,
        runtimeCleanup.RuntimeCleanup.Policy(true, 7.days, 1.day), new CapturingLogger())
      val ids = Seq(buildId, "IU-other-child")
      ids.foreach { id =>
        val marker = registry.marker(project.toPath, id).toFile
        assert(marker.isFile, s"Startup on sbt $sbtVersion must register child build $id in $marker")
        assertProperty(readProperties(marker), "buildId", id, s"Child usage marker $marker on sbt $sbtVersion")
        assertProperty(readProperties(marker), "checkoutRoot", project.getCanonicalPath, s"Child usage marker $marker on sbt $sbtVersion")
      }
      assertFileDoesNotExist(home / "runtime") // Startup only registers metadata.

      // A reload starts a new settings generation and must initialize children again.
      runSbtProcess(Seq("clearStartupUsage", "reload", "show name"), project, vmOptions = vmOptions)
      ids.foreach { id =>
        val marker = registry.marker(project.toPath, id).toFile
        assert(marker.isFile, s"Reload on sbt $sbtVersion must register child build $id again in $marker")
      }
      assertFileDoesNotExist(home / "runtime")
    }
  }

  Seq(SbtProjectFilesUtils.SbtVersionForIntegrationTests, "1.4.5").foreach { sbtVersion =>
    test(s"startup runtime maintenance protects rare development reuse of old test/index paths on sbt $sbtVersion") {
      val home = newTmpDir.toFile / "plugin home with spaces"
      val project = prepareConsumer(home)
      enableRuntimeMaintenanceStartup(project)
      SbtProjectFilesUtils.updateSbtVersion(project, sbtVersion)
      val oldId = "IU-previous-build"
      val resolver = CheckoutRuntimeDirectories(home.toPath, project)
      val tests = resolver.tests(oldId)
      val index = resolver.buildIndex(oldId)
      val paths = Map("test.system" -> tests.system, "test.config" -> tests.config,
        "index.system" -> index.system, "index.config" -> index.config)
      val registry = new runtimeCleanup.RuntimeCleanup(home.toPath,
        runtimeCleanup.RuntimeCleanup.Policy(true, 7.days, 1.day), new CapturingLogger(),
        () => Instant.now().minusSeconds(8.days.toSeconds))
      registry.refresh(project.toPath, oldId, paths)
      paths.values.foreach(path => IO.write(path.resolve("settings.xml").toFile, "persistent development data"))
      val disposable = resolver.tests("IU-unrelated-old-build").system
      registry.refresh(project.toPath, "IU-unrelated-old-build", Map("test.system" -> disposable))
      IO.write(disposable.resolve("expired.txt").toFile, "expired")
      // This is an unusual but valid reassignment, covered for completeness. The
      // child is not aggregated; both backend and frontend overrides must be found.
      IO.append(project / "extra.sbt", s"""
        |ThisBuild / intellijDevSystemDir := file(${quote(tests.system.toFile)})
        |ThisBuild / intellijDevConfigDir := file(${quote(tests.config.toFile)})
        |LocalProject("child") / intellijDevClientSystemDir := file(${quote(index.system.toFile)})
        |LocalProject("child") / intellijDevClientConfigDir := file(${quote(index.config.toFile)})
        |// IDEA setup can initialize test maintenance before the queued startup task.
        |ThisBuild / TaskKey[Unit]("doProjectSetup") := {
        |  TaskKey[Unit]("initializeIntellijRuntimeMaintenance").?.all(ScopeFilter(inAnyProject)).value
        |  ()
        |}
        |""".stripMargin)
      val vmOptions = Seq(s"-Dsbt.global.base=${newTmpDir.resolve("global")}")
      runSbtProcess(Seq("show name"), project, vmOptions = vmOptions)
      paths.values.foreach(path => assertFileContent(path.resolve("settings.xml").toFile, "persistent development data"))
      withClue(s"Reusing old test/index paths for development must withdraw their cleanup ownership on sbt $sbtVersion: ") {
        assertFileDoesNotExist(registry.marker(project.toPath, oldId).toFile)
      }
      withClue(s"Protecting reused development directories must still allow unrelated expired data to be cleaned on sbt $sbtVersion: ") {
        assertFileDoesNotExist(disposable.toFile)
      }
      assertFileDoesNotExist(resolver.tests(buildId).system.toFile)
    }
  }

  test("startup runtime maintenance withdraws development reuse without SDK context and defers unresolved overrides") {
    Seq(false, true).foreach { unavailableOverride =>
      val home = newTmpDir.toFile / "plugin home"
      val project = prepareConsumer(home)
      enableRuntimeMaintenanceStartup(project)
      val oldId = "IU-previous-build"
      val resolver = CheckoutRuntimeDirectories(home.toPath, project)
      val config = resolver.tests(oldId).config
      val disposable = resolver.buildIndex(oldId).system
      val registry = new runtimeCleanup.RuntimeCleanup(home.toPath,
        runtimeCleanup.RuntimeCleanup.Policy(true, 7.days, 1.day), new CapturingLogger(),
        () => Instant.now().minusSeconds(8.days.toSeconds))
      registry.refresh(project.toPath, oldId, Map("test.config" -> config, "index.system" -> disposable))
      IO.write(config.resolve("settings.xml").toFile, "persistent")
      IO.write(disposable.resolve("expired.txt").toFile, "expired")
      val selected = if (unavailableOverride) "sys.error(\"unavailable explicit directory\")" else s"file(${quote(config.toFile)})"
      IO.append(project / "extra.sbt", s"""
        |ThisBuild / intellijDevConfigDir := $selected
        |ThisBuild / TaskKey[IntellijTestRuntimeDirectories.RuntimeDirectories]("defaultDevRuntimeDirectories") := sys.error("unused backend defaults evaluated")
        |ThisBuild / TaskKey[IntellijTestRuntimeDirectories.RuntimeDirectories]("defaultDevClientRuntimeDirectories") := sys.error("unused frontend defaults evaluated")
        |""".stripMargin)
      if (!unavailableOverride)
        IO.append(project / "extra.sbt", "\nThisBuild / intellijRuntimeBuildId := sys.error(\"unavailable SDK identity\")\n")
      runSbtProcess(Seq("show name"), project)
      withClue(s"Unavailable runtime context must defer cleanup (unavailableOverride=$unavailableOverride): ") {
        assertFileDoesNotExist(home / ".sbt-idea-plugin" / "runtime-cleanup-v1.properties")
      }
      val record = readProperties(registry.marker(project.toPath, oldId).toFile)
      if (unavailableOverride) {
        withClue(s"An unresolved development override must retain existing 'test.config' ownership for $config: ") {
          record.getProperty("test.config") should not be null
        }
      }
      else {
        assertProperty(record, "test.config", null, s"Development reuse must withdraw cleanup ownership of $config")
        // A later pass from another checkout respects the persisted withdrawal.
        withClue(s"A peer cleanup pass must delete only the unrelated indexing directory $disposable: ") {
          new runtimeCleanup.RuntimeCleanup(home.toPath, runtimeCleanup.RuntimeCleanup.Policy(true, 7.days, 1.day),
            new CapturingLogger()).cleanupIfDue().directories shouldBe 1
        }
      }
      assertFileContent(config.resolve("settings.xml").toFile, "persistent")
      if (unavailableOverride) {
        withClue("An unresolved development override must defer deletion: ") {
          assertFileExists(disposable.toFile)
        }
      } else {
        withClue("A resolved development override must allow unrelated indexing data to expire: ") {
          assertFileDoesNotExist(disposable.toFile)
        }
      }
    }
  }

  test("startup runtime maintenance recollects subproject overrides after set and releases them after reload") {
    val home = newTmpDir.toFile / "plugin home"
    val project = prepareConsumer(home)
    enableRuntimeMaintenanceStartup(project)
    val registry = new runtimeCleanup.RuntimeCleanup(home.toPath,
      runtimeCleanup.RuntimeCleanup.Policy(true, 7.days, 1.day), new CapturingLogger())
    val marker = registry.marker(project.toPath, buildId).toFile
    val config = CheckoutRuntimeDirectories(home.toPath, project).tests(buildId).config
    IO.append(project / "extra.sbt", s"""
      |TaskKey[Unit]("assertConfigWithdrawn") := {
      |  require(!IO.read(file(${quote(marker)})).contains("test.config="), "A sibling must not re-enroll test.config in " + file(${quote(marker)}))
      |}
      |TaskKey[Unit]("assertConfigManaged") := {
      |  require(IO.read(file(${quote(marker)})).contains("test.config="), "Reload after removing the override must restore test.config ownership in " + file(${quote(marker)}))
      |}
      |""".stripMargin)
    runSbtProcess(Seq(s"""set LocalProject("child") / intellijDevConfigDir := file(${quote(config.toFile)})""",
      "assertConfigWithdrawn", "reload", "assertConfigManaged"), project)
    withClue(s"Reload must restore 'test.config' ownership in usage marker $marker: ") {
      readProperties(marker).getProperty("test.config") should not be null
    }
  }

  test("startup runtime maintenance honors opt-out, custom identity, overrides and unavailable metadata") {
    val home = newTmpDir.toFile / "home"
    val project = prepareConsumer(home)
    enableRuntimeMaintenanceStartup(project)
    val selectedId = "IU-custom-sdk"
    IO.append(project / "extra.sbt", s"""
      |ThisBuild / intellijRuntimeBuildId := "$selectedId"
      |ThisBuild / autoRemoveOldCachedIntelliJRuntimes := false
      |ThisBuild / intellijRuntimeCleanupRetention := scala.concurrent.duration.DurationInt(30).days
      |""".stripMargin)
    runSbtProcess(Seq("show name"), project)
    val registry = new runtimeCleanup.RuntimeCleanup(home.toPath,
      runtimeCleanup.RuntimeCleanup.Policy(true, 7.days, 1.day), new CapturingLogger())
    val marker = registry.marker(project.toPath, selectedId).toFile
    val properties = readProperties(marker)
    assertProperty(properties, "automaticCleanup", "false", s"Cleanup opt-out in $marker")
    assertProperty(properties, "retentionSeconds", "2592000", s"Custom 30-day retention in $marker")

    val previousSystem = CheckoutRuntimeDirectories(home.toPath, project).tests(selectedId).system.toFile
    IO.append(project / "extra.sbt", s"\nThisBuild / intellijTestSystemDir := file(${quote(previousSystem)})\n")
    runSbtProcess(Seq("show name"), project)
    assertProperty(readProperties(marker), "test.system", null, s"Explicit system override must withdraw ownership in $marker")
    withClue(s"An independent system override must retain 'test.config' ownership in $marker: ") {
      readProperties(marker).getProperty("test.config") should not be null
    }

    IO.append(project / "extra.sbt", "\nThisBuild / intellijRuntimeBuildId := sys.error(\"unavailable SDK identity\")\n")
    val unavailable = runSbtProcess(Seq("show name", "showIntellijRuntimeCleanup"), project,
      ioMode = SbtProjectFilesUtils.IoMode.PrintAndCollectOutput)
    // Housekeeping and diagnostics cannot break unrelated commands when SDK metadata is unavailable.
    withClue(s"Cleanup diagnostics in $project must report unavailable SDK identity: ") {
      unavailable.outputLines.getOrElse(fail(s"No captured sbt output for $project")).mkString("\n") should
        include("Cached runtime context: unavailable")
    }
  }

  test("cleanup diagnostics inspect registered runtimes without evaluating runtime tasks or modifying maintenance state") {
    val home = newTmpDir.toFile / "plugin home with spaces"
    val project = prepareConsumer(home)
    // This fixture disables startup maintenance. Unlike the read-only task body, normal sbt
    // startup/completion hooks are allowed to refresh markers and run due cleanup.
    IO.append(project / "extra.sbt", """
      |ThisBuild / intellijRuntimeBuildId := sys.error("diagnostics evaluated runtime identity")
      |ThisBuild / intellijTestSystemDir := sys.error("diagnostics evaluated test system directory")
      |ThisBuild / intellijTestConfigDir := sys.error("diagnostics evaluated test config directory")
      |""".stripMargin)
    val checkout = newTmpDir
    val old = CheckoutRuntimeDirectories(home.toPath, checkout.toFile).tests(buildId)
    IO.write(old.system.resolve("keep.txt").toFile, "keep")
    val registry = new runtimeCleanup.RuntimeCleanup(home.toPath,
      runtimeCleanup.RuntimeCleanup.Policy(true, 7.days, 1.day), new CapturingLogger(),
      () => Instant.now().minusSeconds(8.days.toSeconds))
    registry.refresh(checkout, buildId, Map("test.system" -> old.system, "test.config" -> old.config))
    val marker = registry.marker(checkout, buildId)
    val modified = Files.getLastModifiedTime(marker)
    val content = Files.readAllBytes(marker)
    val output = runSbtProcess(Seq("showIntellijRuntimeCleanup"), project,
      ioMode = SbtProjectFilesUtils.IoMode.PrintAndCollectOutput).outputLines
      .getOrElse(fail(s"No captured cleanup diagnostics for $project")).mkString("\n")
    withClue(s"Cleanup diagnostics for $project must describe the registered runtime without resolving SDK identity: ") {
      output should include(s"Plugin home: ${home.getCanonicalPath}")
      output should include(s"Checkout: ${checkout.toFile.getCanonicalPath}")
      output should include("Cached runtime context: unavailable")
      output should include("Recorded retention: 7 days")
      output should include("Assessment: expired;")
      output should include("[present]")
      output should include("[absent]")
      output should include("index.system: not registered")
      output should include("Normal sbt startup/completion hooks may refresh usage or run automatic cleanup")
    }
    withClue(s"Read-only cleanup diagnostics must not rewrite or touch usage marker $marker: ") {
      Files.readAllBytes(marker) shouldBe content
      Files.getLastModifiedTime(marker) shouldBe modified
    }
    assertFileContent(old.system.resolve("keep.txt").toFile, "keep")
    assertFileDoesNotExist(home / ".sbt-idea-plugin" / "runtime-cleanup-v1.properties")
    assertFileDoesNotExist(home / ".sbt-idea-plugin" / "runtime-cleanup.lock")
  }

  test("minimum supported sbt executes runtime tasks, generation, forks and startup maintenance") {
    val home = newTmpDir.toFile / "plugin home with spaces"
    val project = prepareConsumer(home)
    enableRuntimeMaintenanceStartup(project)
    // Keep in sync with MinimumSbtVersion in build.sbt. Compile-time compatibility alone
    // does not exercise Task.info ownership, dynamic dependencies or lifecycle hooks.
    val minimumSbtVersion = "1.4.5"
    SbtProjectFilesUtils.updateSbtVersion(project, minimumSbtVersion)
    // Personal global plugins may require a newer sbt and are not part of this fixture.
    val vmOptions = Seq(s"-Dsbt.global.base=${newTmpDir.resolve("global")}")
    val oldCheckout = newTmpDir
    val old = CheckoutRuntimeDirectories(home.toPath, oldCheckout.toFile).tests(buildId)
    IO.write(old.system.resolve("expired.txt").toFile, "expired")
    val registry = new runtimeCleanup.RuntimeCleanup(home.toPath,
      runtimeCleanup.RuntimeCleanup.Policy(true, 7.days, 1.day), new CapturingLogger(),
      () => Instant.now().minusSeconds(8.days.toSeconds))
    registry.refresh(oldCheckout, buildId, Map("test.system" -> old.system))

    val output = runSbtProcess(Seq("dumpPaths", "child / dumpPaths", "createIDEARunConfiguration", "probe", "showIntellijRuntimeCleanup"),
      project, vmOptions = vmOptions, ioMode = SbtProjectFilesUtils.IoMode.PrintAndCollectOutput)
    withClue(s"Minimum sbt $minimumSbtVersion must initialize runtime context in $project: ") {
      output.outputLines.getOrElse(fail(s"No captured sbt output for $project")).mkString("\n") should
        include(s"Cached runtime context: $buildId")
    }
    val settings = readProperties(project / "settings.properties")
    assertProperty(settings, "sbt.version", minimumSbtVersion, s"Consumer sbt version in $project")
    withClue(s"Nested sbt in $project must use the parent test JVM's java.home, independently of PATH and JAVA_HOME: ") {
      new File(settings.getProperty("java.home")).getCanonicalFile shouldBe
        new File(System.getProperty("java.home")).getCanonicalFile
    }
    assertProperty(readProperties(project / "child" / "settings.properties"), "idea.system.path",
      settings.getProperty("idea.system.path"), s"Subproject must inherit root test paths on sbt $minimumSbtVersion")
    assertProbeAndTemplateAgree(project, home)
    withClue(s"Startup maintenance on sbt $minimumSbtVersion must delete the expired test system directory: ") {
      assertFileDoesNotExist(old.system.toFile)
    }
    val marker = registry.marker(project.toPath, buildId).toFile
    withClue(s"Startup maintenance on sbt $minimumSbtVersion must register 'test.system' in $marker: ") {
      readProperties(marker).getProperty("test.system") should not be null
    }

    val customId = "IU-minimum-sbt-custom"
    val explicitSystem = home / "explicit test system"
    val reconfigured = runSbtProcess(Seq(
      s"""set ThisBuild / intellijRuntimeBuildId := "$customId"""",
      s"set ThisBuild / intellijTestSystemDir := file(${quote(explicitSystem)})",
      "set ThisBuild / intellijDevUseSharedDirectories := true",
      "showIntellijRuntimeCleanup", "dumpPaths", "createIDEARunConfiguration", "probe", "showIntellijRuntimeCleanup"),
      project, vmOptions = vmOptions, ioMode = SbtProjectFilesUtils.IoMode.PrintAndCollectOutput)
    // Reapplying settings can run startup maintenance again. Both reports must use the
    // new identity rather than retaining the original session's cached context.
    val contexts = reconfigured.outputLines.getOrElse(fail(s"No captured sbt output after reconfiguring $project"))
      .filter(_.contains("Cached runtime context:"))
    withClue(s"Both cleanup reports after 'set' in $project must use the new build identity $customId: ") {
      contexts should have size 2
      contexts.foreach(_ should include(s"Cached runtime context: $customId"))
    }
    val paths = readProperties(project / "settings.properties")
    val context = s"Reconfigured consumer $project on sbt $minimumSbtVersion"
    assertProperty(paths, "idea.system.path", explicitSystem.getAbsolutePath, context)
    assertProperty(paths, "dev.system", (home / "runtime" / "system-shared").getAbsolutePath, context)
    assertProperty(paths, "idea.config.path", CheckoutRuntimeDirectories(home.toPath, project).tests(customId).config.toString, context)
    assertProbeAndTemplateAgree(project, home)
    assertFileDoesNotExist(explicitSystem / "README.txt")
    val changed = readProperties(registry.marker(project.toPath, customId).toFile)
    assertProperty(changed, "test.system", null, s"Explicit system override must not be registered for $customId in $project")
    withClue(s"The independently managed 'test.config' must remain registered for $customId in $project: ") {
      changed.getProperty("test.config") should not be null
    }
  }

  private def enableRuntimeMaintenanceStartup(project: File): Unit = {
    val build = project / "build.sbt"
    IO.write(build, IO.read(build).replace("Global / onLoad := identity",
      "ThisBuild / TaskKey[Unit](\"doProjectSetup\") := { () }"))
  }

  private def prepareConsumer(home: File): File = {
    IO.write(home / "sdk" / "243.22562.145" / "bin" / "probe.vmoptions", "-Dproduct.marker=retained\n")
    IO.write(JbrInstaller.getJbrHome((home / "sdk" / "243.22562.145").toPath).resolve("release").toFile,
      "JAVA_VERSION=\"17.0.1\"\n")
    val copied = copyTestProjectToTemporaryDirectory(testProjectsDir / "checkout-test-directories")
    val project = Files.move(copied.toPath, copied.toPath.resolveSibling("checkout with spaces")).toFile
    SbtProjectFilesUtils.updateSbtVersion(project, SbtProjectFilesUtils.SbtVersionForIntegrationTests)
    SbtProjectFilesUtils.updateSbtIdeaPluginToVersion(project, pluginVersion)
    IO.write(project / "extra.sbt", s"ThisBuild / intellijPluginDirectory := file(${quote(home)})\n")
    val classes = project / "probe classes"
    IO.createDirectory(classes)
    val javac = new File(System.getProperty("java.home")) / "bin" / "javac"
    runProcess(Seq(javac.getAbsolutePath, "--release", "11", "-d", classes.getAbsolutePath,
      (project / "DirectoryProbe.java").getAbsolutePath), project)
    project
  }

  private def assertProbeAndTemplateAgree(project: File, home: File): Unit = withClue(
    s"Runtime outputs for checkout $project:\n") {
    val settings = readProperties(project / "settings.properties")
    val observed = readProperties(project / "observed.properties")
    directoryKeys.foreach { key =>
      assertProperty(observed, key, settings.getProperty(key), "Forked JVM must use the resolved directory")
    }
    assertProperty(observed, "scope.marker", "test", "Forked JVM must retain test VM options")
    assertProperty(observed, "product.marker", "retained", "Forked JVM must retain SDK VM options")
    assertProperty(settings, "sdk", (home / "sdk" / "243.22562.145").getAbsolutePath, "Runtime isolation must preserve the SDK location")
    assertProperty(settings, "downloads", (home / "sdk" / "downloads").getAbsolutePath, "Runtime isolation must preserve the download cache location")
    assertProperty(observed, "idea.plugins.path", (home / "sdk" / "243.22562.145" / "custom-plugins").getAbsolutePath,
      "Forked JVM must use SDK-scoped installed plugins")

    val configs = project / ".idea" / "runConfigurations"
    withClue(s"Generated configurations in $configs must match the requested development and test launches: ") {
      configs.listFiles().iterator.map(_.getName).filter(_.endsWith(".xml")).toSet shouldBe
        Set("root.xml", "root (split mode).xml", "_template__of_JUnit.xml")
    }
    val argfile = configs / "junit_template_argfile.txt"
    val lines = IO.readLines(argfile)
    directoryKeys.foreach { key =>
      withClue(s"JUnit argument file $argfile must contain exactly one authoritative '$key' option: ") {
        lines.filter(_.startsWith(s"-D$key=")) shouldBe Seq("-D" + key + "=\"" + settings.getProperty(key).replace("\\", "\\\\") + "\"")
      }
    }
    withClue(s"JUnit argument file $argfile must retain test VM options: ") {
      lines should contain("-Dscope.marker=test")
    }
    val junitTemplate = configs / "_template__of_JUnit.xml"
    val xml = XML.loadFile(junitTemplate)
    val parameters = (xml \\ "option").find(node => (node \ "@name").text == "VM_PARAMETERS")
      .getOrElse(fail(s"Missing VM_PARAMETERS option in JUnit template $junitTemplate"))
    withClue(s"JUnit template $junitTemplate must reference its quoted argument file: ") {
      (parameters \ "@value").text should include("\"@" + argfile.getCanonicalPath + "\"")
    }
    // Let the Java launcher parse the generated argfile, including paths and classpaths with spaces.
    val java = new File(System.getProperty("java.home")) / "bin" / "java"
    val templateOutput = project / "template.properties"
    runProcess(Seq(java.getAbsolutePath, "@" + argfile.getAbsolutePath, "DirectoryProbe", templateOutput.getAbsolutePath), project)
    val templateObserved = readProperties(templateOutput)
    directoryKeys.foreach { key =>
      assertProperty(templateObserved, key, observed.getProperty(key), s"JVM launched from $argfile must match the sbt fork")
    }
    assertProperty(templateObserved, "scope.marker", observed.getProperty("scope.marker"), "JUnit and sbt must retain the same test VM options")
    val devXml = IO.read(configs / "root.xml")
    withClue(s"Development configuration ${configs / "root.xml"} must use the selected backend paths: ") {
      devXml should include(settings.getProperty("dev.system"))
      devXml should include(settings.getProperty("dev.config"))
    }
    val devConfig = XML.loadFile(configs / "root.xml")
    withClue(s"Log references in ${configs / "root.xml"} must follow the selected backend system directory: ") {
      (devConfig \\ "log_file").map(node => (node \ "@path").text) should contain allOf (
        settings.getProperty("dev.system") + "/log/idea.log",
        settings.getProperty("dev.system") + "/log/build-log/build.log")
    }
    val client = readProperties(configs / "idea_client.properties")
    val clientContext = s"Split frontend properties in ${configs / "idea_client.properties"}"
    assertProperty(client, "idea.system.path", settings.getProperty("client.system"), clientContext)
    assertProperty(client, "idea.config.path", settings.getProperty("client.config"), clientContext)
    assertProperty(client, "idea.log.path", settings.getProperty("client.system") + "/log", clientContext)
    Seq("dev", "index").foreach { prefix =>
      val context = s"$prefix VM options must follow the selected directory keys"
      assertProperty(settings, prefix + ".idea.system.path", settings.getProperty(prefix + ".system"), context)
      assertProperty(settings, prefix + ".idea.config.path", settings.getProperty(prefix + ".config"), context)
      assertProperty(settings, prefix + ".idea.log.path", settings.getProperty(prefix + ".system") + "/log", context)
    }
  }

  private def assertProperty(properties: Properties, key: String, expected: String, context: String): Unit = {
    withClue(s"$context; property '$key': ") {
      properties.getProperty(key) shouldBe expected
    }
  }

  private def assertFileContent(file: File, expected: String): Unit = {
    withClue(s"Contents of $file: ") {
      IO.read(file) shouldBe expected
    }
  }

  private def readProperties(file: File): Properties = {
    assertFileExists(file)
    val properties = new Properties
    val in = Files.newInputStream(file.toPath)
    try properties.load(in) finally in.close()
    properties
  }

  private def quote(file: File): String =
    "\"" + file.getAbsolutePath.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
