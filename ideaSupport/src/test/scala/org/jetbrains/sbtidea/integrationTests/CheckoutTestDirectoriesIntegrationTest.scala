package org.jetbrains.sbtidea.integrationTests

import org.jetbrains.sbtidea.CheckoutRuntimeDirectories
import org.jetbrains.sbtidea.download.jbr.JbrInstaller
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils.{runProcess, runSbtProcess}
import org.scalatest.matchers.should.Matchers
import sbt.{File, IO, fileToRichFile}

import java.nio.file.Files
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
