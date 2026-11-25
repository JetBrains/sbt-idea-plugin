package org.jetbrains.sbtidea.integrationTests

import org.jetbrains.sbtidea.{IntelliJPlatform, IntellijTestRuntimeDirectories, JbrPlatform}
import org.jetbrains.sbtidea.productInfo.{OS, ProductInfoExtraDataProviderImpl, ProductInfoParser}
import org.jetbrains.sbtidea.runIdea.{CustomIntellijVMOptions, IntellijAwareRunner, IntellijVMOptionsBuilder}
import org.jetbrains.sbtidea.testUtils.SbtProjectFilesUtils.runProcess
import org.scalatest.matchers.should.Matchers
import sbt.{File, IO, fileToRichFile}

import java.nio.file.{Files, Paths}
import java.util.Properties

class RuntimePropertiesIntegrationTest extends SbtIdeaPluginIntegrationTestBase with Matchers {
  private lazy val sdk = runUpdateIntellijCommand("simple-ultimate-edition")
  private lazy val productInfo = ProductInfoParser.parse(sdk / "product-info.json")
  private lazy val provider = new ProductInfoExtraDataProviderImpl(sdk, productInfo, productInfo.launchFor(JbrPlatform.auto))

  test("development properties load without importing production-profile properties") {
    assertPropertyLoading(forTests = false, runtimeFile = true, customFile = false)
  }

  test("test properties load without importing production-profile properties") {
    assertPropertyLoading(forTests = true, runtimeFile = true, customFile = false)
  }

  test("a missing runtime properties file does not enable production-profile fallback") {
    assertPropertyLoading(forTests = false, runtimeFile = false, customFile = false)
  }

  test("an explicit properties file replaces the runtime default without importing production properties") {
    assertPropertyLoading(forTests = false, runtimeFile = true, customFile = true)
  }

  private def assertPropertyLoading(forTests: Boolean, runtimeFile: Boolean, customFile: Boolean): Unit = {
    val root = newTmpDir.toFile
    val home = root / "temporary user home"
    val config = root / "runtime config with spaces"
    val directories = IntellijTestRuntimeDirectories.RuntimeDirectories(
      (root / "system").toPath, config.toPath, (root / "system" / "log").toPath)
    val defaultFile = config / "idea.properties"
    val explicitFile = root / "custom properties" / "idea.properties"
    if (runtimeFile) IO.write(defaultFile, "scl25595.runtime=loaded\n")
    if (customFile) IO.write(explicitFile, "scl25595.custom=loaded\n")

    val builder = new IntellijVMOptionsBuilder(IntelliJPlatform.Idea, provider, (root / "plugin").toPath,
      (root / "plugin home").toPath, sdk.toPath, testRuntimeDirectories = Some(directories), devRuntimeDirectories = Some(directories))
    val custom = Seq("-Djava.awt.headless=true", s"-Didea.home.path=${sdk.getAbsolutePath}", s"-Duser.home=$home") ++
      (if (customFile) Seq(s"-Didea.properties.file=$explicitFile") else Nil)
    val options = builder.build(CustomIntellijVMOptions(debugInfo = None, extraOptions = custom),
      forTests = forTests, quoteValues = false)
    // The fixture must include the same production selector the SDK normally contributes.
    val productionSelector = provider.vmOptionsAll.find(_.startsWith("-Didea.paths.selector="))
      .getOrElse(fail("The SDK fixture must supply a production selector")).stripPrefix("-Didea.paths.selector=")
    val javaExecutable = IntellijAwareRunner.getBundledJRE(sdk.toPath).getOrElse(fail(s"Missing SDK JBR in $sdk")).root
      .resolve("bin").resolve(if (OS.current == OS.Windows) "java.exe" else "java")
    val classes = Paths.get(classOf[RuntimePropertiesProbe].getProtectionDomain.getCodeSource.getLocation.toURI).toFile
    val classpath = Seq(classes, sdk / "lib" / "*").map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)
    val output = root / "observed.properties"
    // A fresh JVM prevents PathManager's cached selector/config from concealing the regression.
    runProcess(Seq(javaExecutable.toString) ++ options ++ Seq("-cp", classpath, classOf[RuntimePropertiesProbe].getName,
      output.getAbsolutePath, productionSelector), root, envVars = Map(
      "APPDATA" -> (home / "AppData" / "Roaming").getPath,
      "LOCALAPPDATA" -> (home / "AppData" / "Local").getPath,
      "XDG_CONFIG_HOME" -> (home / ".config").getPath))
    val observed = new Properties
    val input = Files.newInputStream(output.toPath)
    try observed.load(input) finally input.close()
    observed.getProperty("scl25595.production") shouldBe "<absent>"
    observed.getProperty("scl25595.runtime") shouldBe (if (runtimeFile && !customFile) "loaded" else "<absent>")
    observed.getProperty("scl25595.custom") shouldBe (if (customFile) "loaded" else "<absent>")
    observed.getProperty("resolved.config") shouldBe config.getPath
    observed.getProperty("idea.properties.file") shouldBe (if (customFile) explicitFile else defaultFile).getPath
    if (!runtimeFile) assert(!defaultFile.exists(), "Loading missing properties must not create an empty file")
  }
}
