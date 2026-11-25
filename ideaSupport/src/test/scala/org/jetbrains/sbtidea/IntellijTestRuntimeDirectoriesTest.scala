package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.productInfo.ProductInfoExtraDataProvider
import org.jetbrains.sbtidea.runIdea.{CustomIntellijVMOptions, IntellijVMOptionsBuilder}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sbt.ForkOptions

import java.io.File
import java.nio.file.Paths

class IntellijTestRuntimeDirectoriesTest extends AnyFunSuite with Matchers {
  private val ideaHome = Paths.get("idea home")
  private val directories = IntellijTestRuntimeDirectories(ideaHome)

  test("uses an isolated IDEA system, config, and log directory for a runtime ID") {
    directories.forRuntimeId("tc-123_outer-5.inner-1").jvmOptions shouldBe Vector(
      "-Didea.system.path=idea home/test-system-tc-123_outer-5.inner-1",
      "-Didea.config.path=idea home/test-config-tc-123_outer-5.inner-1",
      "-Didea.log.path=idea home/test-system-tc-123_outer-5.inner-1/log",
    )
  }

  test("replaces only IntelliJ test directory options in fork options") {
    val initialJvmOptions: Vector[String] = Vector(
      "-Xmx2g",
      "-Didea.system.path=shared-system",
      "-Didea.config.path=shared-config",
      "-Didea.log.path=shared-log",
      "-Didea.plugins.path=shared-plugins",
      "-Dtest.option=value",
    )
    val forkOptions = ForkOptions().withRunJVMOptions(initialJvmOptions)

    val isolated = directories.applyTo(forkOptions, "run-42")

    isolated.runJVMOptions.filterNot(option =>
      option.startsWith("-Didea.paths.selector=") || option.startsWith("-Didea.properties.file=")) shouldBe Vector(
      "-Xmx2g",
      "-Dtest.option=value",
      "-Didea.system.path=idea home/test-system-run-42",
      "-Didea.config.path=idea home/test-config-run-42",
      "-Didea.log.path=idea home/test-system-run-42/log",
      s"-Didea.plugins.path=${ideaHome.resolve("test-config-run-42").resolve("plugins")}",
    )
    property(isolated, "idea.paths.selector") should fullyMatch regex "SbtIdeaPlugin-[0-9a-f]{16}"
    property(isolated, "idea.properties.file") shouldBe ideaHome.resolve("test-config-run-42/idea.properties").toString
  }

  test("relocates inherited generated properties defaults, including repeated runtime isolation") {
    val original = forkOptions()
    val isolated = directories.applyTo(original, "first")
    val relocated = directories.applyTo(isolated, "second")
    property(isolated, "idea.paths.selector") should not be property(original, "idea.paths.selector")
    property(relocated, "idea.paths.selector") should not be property(isolated, "idea.paths.selector")
    property(relocated, "idea.properties.file") shouldBe ideaHome.resolve("test-config-second/idea.properties").toString
    relocated.runJVMOptions.count(_.startsWith("-Didea.properties.file=")) shouldBe 1
    relocated.runJVMOptions.count(_.startsWith("-Didea.paths.selector=")) shouldBe 1
  }

  test("preserves explicit properties-file and selector overrides during runtime isolation") {
    val original = forkOptions(Seq("-Didea.properties.file=custom file.properties", "-Didea.paths.selector=ExplicitProfile"))
    val isolated = directories.applyTo(original, "custom")
    property(isolated, "idea.properties.file") shouldBe "custom file.properties"
    property(isolated, "idea.paths.selector") shouldBe "ExplicitProfile"
    property(isolated, "idea.config.path") shouldBe ideaHome.resolve("test-config-custom").toString
  }

  private def forkOptions(extraOptions: Seq[String] = Nil): ForkOptions = {
    val provider = new ProductInfoExtraDataProvider {
      override def vmOptionsAll: Seq[String] = Seq("-Didea.paths.selector=IntelliJIdea2024.3")
      override def bootClasspathJars: Seq[File] = fail("Unused")
      override def productModulesJars: Seq[File] = fail("Unused")
      override def testFrameworkJars: Seq[File] = fail("Unused")
    }
    val builder = new IntellijVMOptionsBuilder(IntelliJPlatform.Idea, provider, Paths.get("plugin"), ideaHome, Paths.get("sdk"))
    ForkOptions().withRunJVMOptions(builder.build(CustomIntellijVMOptions(debugInfo = None, extraOptions = extraOptions),
      forTests = true, quoteValues = false).toVector)
  }

  private def property(options: ForkOptions, key: String): String = {
    val prefix = s"-D$key="
    options.runJVMOptions.reverseIterator.find(_.startsWith(prefix))
      .getOrElse(fail(s"Missing $key in ${options.runJVMOptions}")).stripPrefix(prefix)
  }

  test("rejects unsafe runtime IDs") {
    Seq("", ".", "../other", "outer/inner", "has space").foreach { runtimeId =>
      an[IllegalArgumentException] should be thrownBy directories.forRuntimeId(runtimeId)
    }
  }
}
