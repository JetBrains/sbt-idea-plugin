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
    firstDirs.system should not be secondDirs.system

    runSbtProcess(Seq("dumpPaths", "child / dumpPaths"), first)
    val expected = readProperties(first / "settings.properties")
    directoryKeys.foreach { key =>
      readProperties(first / "child" / "settings.properties").getProperty(key) shouldBe expected.getProperty(key)
    }
    Files.exists(firstDirs.system) shouldBe false
    Files.exists(firstDirs.config) shouldBe false

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
      assertProbeAndTemplateAgree(project, home, legacy = false)
      val dirs = CheckoutRuntimeDirectories(home.toPath, project).tests(buildId)
      Seq(dirs.system, dirs.config).foreach { dir =>
        IO.read(dir.resolve("README.txt").toFile) should include(project.getCanonicalPath)
      }
      val readme = dirs.system.resolve("README.txt")
      val timestamp = Files.getLastModifiedTime(readme)
      runSbtProcess(Seq("createIDEARunConfiguration"), project)
      Files.getLastModifiedTime(readme) shouldBe timestamp
    }

    runSbtProcess(Seq("cleanUpTestEnvironment"), first)
    Files.exists(firstDirs.system) shouldBe false
    Files.exists(firstDirs.config) shouldBe false
    Files.exists(secondDirs.system.resolve("README.txt")) shouldBe true
    Files.exists(secondDirs.config.resolve("README.txt")) shouldBe true
    sentinels.foreach(file => IO.read(file) shouldBe "keep")
    previousSentinels.foreach(file => IO.read(file) shouldBe "keep")
    IO.read(otherBuild.system.resolve("keep.txt").toFile) shouldBe "keep"
  }

  test("legacy VM options, scoped directory overrides, JUnit generation and cleanup use the same selected paths") {
    val home = newTmpDir.toFile / "plugin home"
    val project = prepareConsumer(home)
    val system = home / "explicit system"
    val config = home / "explicit config"
    val defaults = CheckoutRuntimeDirectories(home.toPath, project).tests(buildId)
    IO.write(defaults.system.resolve("keep.txt").toFile, "keep")
    IO.write(defaults.config.resolve("keep.txt").toFile, "keep")
    IO.append(project / "extra.sbt",
      s"""
         |LocalRootProject / Test / useNewVmOptions := false
         |LocalRootProject / useNewVmOptions := false
         |LocalRootProject / Test / intellijTestSystemDir := file(${quote(system)})
         |ThisBuild / intellijTestConfigDir := file(${quote(config)})
         |""".stripMargin)

    runSbtProcess(Seq("probe", "dumpPaths", "createIDEARunConfiguration"), project)
    val properties = readProperties(project / "settings.properties")
    properties.getProperty("idea.system.path") shouldBe system.getAbsolutePath
    properties.getProperty("idea.config.path") shouldBe config.getAbsolutePath
    assertProbeAndTemplateAgree(project, home, legacy = true)
    (system / "README.txt").exists() shouldBe false
    (config / "README.txt").exists() shouldBe false
    Files.exists(defaults.system.resolve("README.txt")) shouldBe false
    Files.exists(defaults.config.resolve("README.txt")) shouldBe false

    runSbtProcess(Seq("cleanUpTestEnvironment"), project)
    system.exists() shouldBe false
    config.exists() shouldBe false
    IO.read(defaults.system.resolve("keep.txt").toFile) shouldBe "keep"
    IO.read(defaults.config.resolve("keep.txt").toFile) shouldBe "keep"
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
      firstPaths.getProperty(key) shouldBe (home / "runtime" / name).getAbsolutePath
      secondPaths.getProperty(key) shouldBe firstPaths.getProperty(key)
    }
    Seq("idea.system.path", "idea.config.path", "index.system", "index.config").foreach { key =>
      firstPaths.getProperty(key) should not be secondPaths.getProperty(key)
    }
    secondPaths.getProperty("idea.system.path") shouldBe
      (home / "runtime" / s"IU-$secondBuild" / CheckoutRuntimeDirectories(home.toPath, second).checkoutHash /
        "tests" / "system").getAbsolutePath
    Seq(first, second).foreach(assertProbeAndTemplateAgree(_, home, legacy = false))

    runSbtProcess(Seq("set ThisBuild / intellijDevUseSharedDirectories := false") ++ commands,
      first, vmOptions = Seq("-Dsbtidea.dev.sharedDirectories=true"))
    val isolated = readProperties(first / "settings.properties")
    isolated.getProperty("sharing") shouldBe "false"
    isolated.getProperty("dev.system") shouldBe CheckoutRuntimeDirectories(home.toPath, first).dev(buildId).system.toString
    isolated.getProperty("client.config") shouldBe CheckoutRuntimeDirectories(home.toPath, first).devClient(buildId).config.toString
    assertProbeAndTemplateAgree(first, home, legacy = false)

    val customDevSystem = home / "selected dev system"
    runSbtProcess(Seq("set ThisBuild / intellijDevUseSharedDirectories := true",
      s"set ThisBuild / intellijDevSystemDir := file(${quote(customDevSystem)})", "dumpPaths"),
      second, vmOptions = Seq("-Dsbtidea.dev.sharedDirectories=false", s"-DdirectoryProbe.build=$secondBuild"))
    val mixed = readProperties(second / "settings.properties")
    mixed.getProperty("dev.config") shouldBe firstPaths.getProperty("dev.config")
    mixed.getProperty("dev.system") shouldBe customDevSystem.getAbsolutePath
    mixed.getProperty("client.system") shouldBe firstPaths.getProperty("client.system")
    (home / "runtime" / "config-shared").exists() shouldBe false // generation does not initialize dev config
  }

  test("an explicit build ID selects every isolated purpose and bypasses missing SDK metadata for path inspection") {
    val home = newTmpDir.toFile / "home"
    val project = prepareConsumer(home)
    val customId = "IU-243.22562.145-custom-2"
    IO.append(project / "extra.sbt", s"""
      |ThisBuild / intellijRuntimeBuildId := "$customId"
      |""".stripMargin)
    runSbtProcess(Seq("dumpPaths", "createIDEARunConfiguration", "probe"), project)
    assertProbeAndTemplateAgree(project, home, legacy = false)
    val paths = readProperties(project / "settings.properties")
    val resolver = CheckoutRuntimeDirectories(home.toPath, project)
    paths.getProperty("idea.system.path") shouldBe resolver.tests(customId).system.toString
    paths.getProperty("dev.system") shouldBe resolver.dev(customId).system.toString
    paths.getProperty("client.config") shouldBe resolver.devClient(customId).config.toString
    paths.getProperty("index.config") shouldBe resolver.buildIndex(customId).config.toString
    IO.read(resolver.tests(customId).system.resolve("README.txt").toFile) should include(customId)

    runSbtProcess(Seq("set ThisBuild / productInfo := sys.error(\"unused SDK metadata evaluated\")",
      "show Test / intellijTestConfigDir", "show intellijDevSystemDir", "show intellijDevClientConfigDir",
      "set ThisBuild / intellijRuntimeBuildId := sys.error(\"unused identity evaluated\")",
      "set ThisBuild / intellijDevUseSharedDirectories := true",
      "show intellijDevSystemDir", "show intellijDevConfigDir",
      "show intellijDevClientSystemDir", "show intellijDevClientConfigDir"), project)
    Files.exists(resolver.dev(customId).system) shouldBe false
    Files.exists(resolver.buildIndex(customId).system) shouldBe false
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
    actual.getProperty("idea.system.path") shouldBe paths(0).getAbsolutePath
    actual.getProperty("idea.config.path") shouldBe paths(1).getAbsolutePath
    paths.take(2).foreach(_.exists() shouldBe false)
    paths.drop(2).foreach(path => IO.read(path / "keep.txt") shouldBe "keep")
    paths.foreach(path => (path / "README.txt").exists() shouldBe false)
    val configs = project / ".idea" / "runConfigurations"
    val xml = IO.read(configs / "root.xml")
    xml should include(paths(2).getAbsolutePath)
    xml should include(paths(3).getAbsolutePath)
    val client = readProperties(configs / "idea_client.properties")
    client.getProperty("idea.system.path") shouldBe paths(4).getAbsolutePath
    client.getProperty("idea.config.path") shouldBe paths(5).getAbsolutePath

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
    observed.getProperty("idea.system.path") shouldBe pinned.system.toString
    observed.getProperty("idea.config.path") shouldBe pinned.config.toString
    Seq(pinned.system, pinned.config).foreach { dir =>
      Files.exists(dir.resolve("README.txt")) shouldBe false
    }

    // A user override equal to a current default is still user-owned; path equality is not provenance.
    val current = resolver.tests(buildId)
    runSbtProcess(Seq(
      s"""set ThisBuild / intellijRuntimeBuildId := "$buildId" """,
      s"set Test / intellijTestSystemDir := file(${quote(current.system.toFile)})",
      s"set Test / intellijTestConfigDir := file(${quote(current.config.toFile)})",
      "createIDEARunConfiguration", "probe"), project)
    Seq(current.system, current.config).foreach { dir =>
      Files.exists(dir.resolve("README.txt")) shouldBe false
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
    Files.exists(directories.system.resolve("README.txt")) shouldBe false
    Files.exists(directories.config.resolve("README.txt")) shouldBe true
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
    IO.read(configs / "root.xml") should include(expected.system.toString)
    (configs / "_template__of_JUnit.xml").isFile shouldBe true
    (configs / "junit_template_argfile.txt").isFile shouldBe true
    (configs / "root (split mode).xml").exists() shouldBe false
    (configs / "idea_client.properties").exists() shouldBe false
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
      IO.read(configs / output) should include((home / "runtime" / "system-shared").getAbsolutePath)
      (configs / "_template__of_JUnit.xml").exists() shouldBe false
      (configs / "junit_template_argfile.txt").exists() shouldBe false
      Files.exists(CheckoutRuntimeDirectories(home.toPath, project).tests(buildId).system) shouldBe false
      Files.exists(CheckoutRuntimeDirectories(home.toPath, project).tests(buildId).config) shouldBe false
      if (output == "root (split mode).xml") {
        readProperties(configs / "idea_client.properties").getProperty("idea.system.path") shouldBe
          (home / "runtime" / "system-client-shared").getAbsolutePath
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
    configs.list().toSet shouldBe Set("_template__of_JUnit.xml", "junit_template_argfile.txt")
    val java = new File(System.getProperty("java.home")) / "bin" / "java"
    val observed = project / "junit-only.properties"
    runProcess(Seq(java.getAbsolutePath, "@" + (configs / "junit_template_argfile.txt").getAbsolutePath,
      "DirectoryProbe", observed.getAbsolutePath), project)
    val properties = readProperties(observed)
    properties.getProperty("idea.system.path") shouldBe system.getAbsolutePath
    properties.getProperty("idea.config.path") shouldBe config.getAbsolutePath
    properties.getProperty("idea.log.path") shouldBe (system / "log").getAbsolutePath
    Seq(system, config).foreach(path => (path / "README.txt").exists() shouldBe false)
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
    (project / ".idea" / "runConfigurations").exists() shouldBe false
    (home / "runtime").exists() shouldBe false
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
    paths.values.foreach(path => Files.exists(path) shouldBe false)
    keep.foreach(path => IO.read(path) shouldBe "keep")
    val ownMarker = registry.marker(project.toPath, buildId).toFile
    ownMarker.isFile shouldBe true
    IO.read(project / "completion-hook.txt") shouldBe "called"
    val record = readProperties(ownMarker)
    record.getProperty("retentionSeconds") shouldBe "604800"
    record.getProperty("automaticCleanup") shouldBe "true"
    val current = CheckoutRuntimeDirectories(home.toPath, project).tests(buildId)
    Files.exists(current.system) shouldBe false // Startup registers metadata, not initialized caches.

    paths.values.foreach(path => IO.write(path.resolve("expired.txt").toFile, "expired"))
    registry.refresh(oldCheckout, buildId, paths)
    runSbtProcess(Seq("show name"), project)
    paths.values.foreach(path => Files.exists(path.resolve("expired.txt")) shouldBe true)
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
        |    require(markers.size == 2, "Both plugin children must have initialized on load")
        |    IO.delete(markers)
        |    require(markers.forall(!_.exists()), "Test-owned usage records should be absent before reload")
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
        marker.isFile shouldBe true
        readProperties(marker).getProperty("buildId") shouldBe id
        readProperties(marker).getProperty("checkoutRoot") shouldBe project.getCanonicalPath
      }
      (home / "runtime").exists() shouldBe false // Startup only registers metadata.

      // A reload starts a new settings generation and must initialize children again.
      runSbtProcess(Seq("clearStartupUsage", "reload", "show name"), project, vmOptions = vmOptions)
      ids.foreach(id => registry.marker(project.toPath, id).toFile.isFile shouldBe true)
      (home / "runtime").exists() shouldBe false
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
      paths.values.foreach(path => IO.read(path.resolve("settings.xml").toFile) shouldBe "persistent development data")
      registry.marker(project.toPath, oldId).toFile.exists() shouldBe false
      Files.exists(disposable) shouldBe false
      Files.exists(resolver.tests(buildId).system) shouldBe false
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
      Files.exists(home.toPath.resolve(".sbt-idea-plugin/runtime-cleanup-v1.properties")) shouldBe false
      val record = readProperties(registry.marker(project.toPath, oldId).toFile)
      if (unavailableOverride) record.getProperty("test.config") should not be null
      else {
        record.getProperty("test.config") shouldBe null
        // A later pass from another checkout respects the persisted withdrawal.
        new runtimeCleanup.RuntimeCleanup(home.toPath, runtimeCleanup.RuntimeCleanup.Policy(true, 7.days, 1.day),
          new CapturingLogger()).cleanupIfDue().directories shouldBe 1
      }
      IO.read(config.resolve("settings.xml").toFile) shouldBe "persistent"
      Files.exists(disposable) shouldBe unavailableOverride
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
      |  require(!IO.read(file(${quote(marker)})).contains("test.config="), "A sibling must not re-enroll the override")
      |}
      |TaskKey[Unit]("assertConfigManaged") := {
      |  require(IO.read(file(${quote(marker)})).contains("test.config="), "Removing the override restores ordinary ownership")
      |}
      |""".stripMargin)
    runSbtProcess(Seq(s"""set LocalProject("child") / intellijDevConfigDir := file(${quote(config.toFile)})""",
      "assertConfigWithdrawn", "reload", "assertConfigManaged"), project)
    readProperties(marker).getProperty("test.config") should not be null
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
    properties.getProperty("automaticCleanup") shouldBe "false"
    properties.getProperty("retentionSeconds") shouldBe "2592000"

    val previousSystem = CheckoutRuntimeDirectories(home.toPath, project).tests(selectedId).system.toFile
    IO.append(project / "extra.sbt", s"\nThisBuild / intellijTestSystemDir := file(${quote(previousSystem)})\n")
    runSbtProcess(Seq("show name"), project)
    readProperties(marker).getProperty("test.system") shouldBe null
    readProperties(marker).getProperty("test.config") should not be null

    IO.append(project / "extra.sbt", "\nThisBuild / intellijRuntimeBuildId := sys.error(\"unavailable SDK identity\")\n")
    val unavailable = runSbtProcess(Seq("show name", "showIntellijRuntimeCleanup"), project,
      ioMode = SbtProjectFilesUtils.IoMode.PrintAndCollectOutput)
    // Housekeeping and diagnostics cannot break unrelated commands when SDK metadata is unavailable.
    unavailable.outputLines.get.mkString("\n") should include("Cached runtime context: unavailable")
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
      ioMode = SbtProjectFilesUtils.IoMode.PrintAndCollectOutput).outputLines.get.mkString("\n")
    output should include(s"Plugin home: ${home.getCanonicalPath}")
    output should include(s"Checkout: ${checkout.toFile.getCanonicalPath}")
    output should include("Cached runtime context: unavailable")
    output should include("Recorded retention: 7 days")
    output should include("Assessment: expired;")
    output should include("[present]")
    output should include("[absent]")
    output should include("index.system: not registered")
    output should include("Normal sbt startup/completion hooks may refresh usage or run automatic cleanup")
    Files.readAllBytes(marker) shouldBe content
    Files.getLastModifiedTime(marker) shouldBe modified
    IO.read(old.system.resolve("keep.txt").toFile) shouldBe "keep"
    Files.exists(home.toPath.resolve(".sbt-idea-plugin/runtime-cleanup-v1.properties")) shouldBe false
    Files.exists(home.toPath.resolve(".sbt-idea-plugin/runtime-cleanup.lock")) shouldBe false
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
    output.outputLines.get.mkString("\n") should include(s"Cached runtime context: $buildId")
    readProperties(project / "settings.properties").getProperty("sbt.version") shouldBe minimumSbtVersion
    new File(readProperties(project / "settings.properties").getProperty("java.home")).getCanonicalFile shouldBe
      new File(System.getProperty("java.home")).getCanonicalFile
    readProperties(project / "child" / "settings.properties").getProperty("idea.system.path") shouldBe
      readProperties(project / "settings.properties").getProperty("idea.system.path")
    assertProbeAndTemplateAgree(project, home, legacy = false)
    Files.exists(old.system) shouldBe false
    val marker = registry.marker(project.toPath, buildId).toFile
    readProperties(marker).getProperty("test.system") should not be null

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
    val contexts = reconfigured.outputLines.get.filter(_.contains("Cached runtime context:"))
    contexts should have size 2
    contexts.foreach(_ should include(s"Cached runtime context: $customId"))
    val paths = readProperties(project / "settings.properties")
    paths.getProperty("idea.system.path") shouldBe explicitSystem.getAbsolutePath
    paths.getProperty("dev.system") shouldBe (home / "runtime" / "system-shared").getAbsolutePath
    paths.getProperty("idea.config.path") shouldBe
      CheckoutRuntimeDirectories(home.toPath, project).tests(customId).config.toString
    assertProbeAndTemplateAgree(project, home, legacy = false)
    (explicitSystem / "README.txt").exists() shouldBe false
    val changed = readProperties(registry.marker(project.toPath, customId).toFile)
    changed.getProperty("test.system") shouldBe null
    changed.getProperty("test.config") should not be null
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

  private def assertProbeAndTemplateAgree(project: File, home: File, legacy: Boolean): Unit = {
    val settings = readProperties(project / "settings.properties")
    val observed = readProperties(project / "observed.properties")
    directoryKeys.foreach(key => observed.getProperty(key) shouldBe settings.getProperty(key))
    observed.getProperty("scope.marker") shouldBe (if (legacy) "legacy-test" else "test")
    if (!legacy) observed.getProperty("product.marker") shouldBe "retained"
    settings.getProperty("sdk") shouldBe (home / "sdk" / "243.22562.145").getAbsolutePath
    settings.getProperty("downloads") shouldBe (home / "sdk" / "downloads").getAbsolutePath
    observed.getProperty("idea.plugins.path") shouldBe (home / "sdk" / "243.22562.145" / "custom-plugins").getAbsolutePath

    val configs = project / ".idea" / "runConfigurations"
    val argfile = configs / "junit_template_argfile.txt"
    val lines = IO.readLines(argfile)
    directoryKeys.foreach { key =>
      lines.filter(_.startsWith(s"-D$key=")) shouldBe Seq("-D" + key + "=\"" + settings.getProperty(key).replace("\\", "\\\\") + "\"")
    }
    lines should contain(s"-Dscope.marker=${if (legacy) "legacy-test" else "test"}")
    val xml = XML.loadFile(configs / "_template__of_JUnit.xml")
    val parameters = (xml \\ "option").find(node => (node \ "@name").text == "VM_PARAMETERS").get
    (parameters \ "@value").text should include("\"@" + argfile.getCanonicalPath + "\"")
    // Let the Java launcher parse the generated argfile, including paths and classpaths with spaces.
    val java = new File(System.getProperty("java.home")) / "bin" / "java"
    val templateOutput = project / "template.properties"
    runProcess(Seq(java.getAbsolutePath, "@" + argfile.getAbsolutePath, "DirectoryProbe", templateOutput.getAbsolutePath), project)
    val templateObserved = readProperties(templateOutput)
    directoryKeys.foreach(key => templateObserved.getProperty(key) shouldBe observed.getProperty(key))
    templateObserved.getProperty("scope.marker") shouldBe observed.getProperty("scope.marker")
    val devXml = IO.read(configs / "root.xml")
    devXml should include(settings.getProperty("dev.system"))
    devXml should include(settings.getProperty("dev.config"))
    val devConfig = XML.loadFile(configs / "root.xml")
    (devConfig \\ "log_file").map(node => (node \ "@path").text) should contain allOf (
      settings.getProperty("dev.system") + "/log/idea.log",
      settings.getProperty("dev.system") + "/log/build-log/build.log")
    val client = readProperties(configs / "idea_client.properties")
    client.getProperty("idea.system.path") shouldBe settings.getProperty("client.system")
    client.getProperty("idea.config.path") shouldBe settings.getProperty("client.config")
    client.getProperty("idea.log.path") shouldBe settings.getProperty("client.system") + "/log"
    Seq("dev", "index").foreach { prefix =>
      settings.getProperty(prefix + ".idea.system.path") shouldBe settings.getProperty(prefix + ".system")
      settings.getProperty(prefix + ".idea.config.path") shouldBe settings.getProperty(prefix + ".config")
      settings.getProperty(prefix + ".idea.log.path") shouldBe settings.getProperty(prefix + ".system") + "/log"
    }
  }

  private def readProperties(file: File): Properties = {
    val properties = new Properties
    val in = Files.newInputStream(file.toPath)
    try properties.load(in) finally in.close()
    properties
  }

  private def quote(file: File): String =
    "\"" + file.getAbsolutePath.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
