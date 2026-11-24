package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.IntelliJPlatform.Idea
import org.jetbrains.sbtidea.productInfo.ProductInfoExtraDataProvider
import org.jetbrains.sbtidea.runIdea.CustomIntellijVMOptions.DebugInfo
import org.jetbrains.sbtidea.runIdea.{CustomIntellijVMOptions, IntellijVMOptionsBuilder}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.{Path, Paths}

class IdeaVmOptionsTest extends AnyFunSuite with Matchers {

  private val extraVmOptions = Seq("-Dkey1=value", "-Dkey2=\"value\"")

  private val mockIntellijBaseDir: Path = Paths.get("test non-existent intellijDirectory")

  private val vmOptions = CustomIntellijVMOptions(
    xmx = Some(1024),
    xms = Some(128),
    debugInfo = Some(DebugInfo.Default),
    extraOptions = extraVmOptions
  )

  //noinspection NotImplementedCode
  private val mockProductInfoExtraDataProvider = new ProductInfoExtraDataProvider {
    override def vmOptionsAll: Seq[String] = Seq(
      """-Xms128m""",
      """-Xmx2048m""",
      """-Dkey1=value""",
      """-Dkey2="value"""",
      """-Dkey3=""""",
    )

    override def bootClasspathJars: Seq[File] = ??? // shouldn't be used in the test

    override def productModulesJars: Seq[File] = ??? // shouldn't be used in the test

    override def testFrameworkJars: Seq[File] = ??? // shouldn't be used in the test
  }

  private val vmOptionsBuilder = new IntellijVMOptionsBuilder(
    platform = Idea,
    productInfoExtraDataProvider = mockProductInfoExtraDataProvider,
    pluginPath = Paths.get("foo bar"),
    ideaHome = Paths.get("bar baz"),
    intellijDirectory = mockIntellijBaseDir,
  )

  private val Q = "&quot;"

  private val mustBeQuoted = Seq(
    "plugin.path",
    "idea.config.path",
    "idea.plugins.path",
    "idea.system.path",
  )

  test("paths are quoted in xml version") {
    val asSeq = vmOptionsBuilder.build(vmOptions, forTests = false, quoteValues = true)
    val quotedKeys = asSeq.filter(s => mustBeQuoted.exists(s.contains(_)))
    quotedKeys.size shouldBe mustBeQuoted.size
    quotedKeys.foreach { option =>
      option should endWith regex s"=$Q.+$Q$$"
    }
  }

  test("paths are NOT quoted in SBT version") {
    val asSeq = vmOptionsBuilder.build(vmOptions, forTests = false, quoteValues = false)
    val quotedKeys = asSeq.filter(s => mustBeQuoted.exists(s.contains(_)))
    quotedKeys.size shouldBe mustBeQuoted.size
    quotedKeys.foreach { option =>
      option shouldNot endWith regex s"=$Q.+$Q$$"
    }
  }

  test("development options normalize selected system/config/log paths") {
    assertRuntimeDirectories(purpose = "dev")
  }

  test("indexing options normalize selected system/config/log paths") {
    assertRuntimeDirectories(purpose = "build-index")
  }

  test("test options use resolved directories and retain unrelated options") {
    val dirs = IntellijTestRuntimeDirectories.RuntimeDirectories(
      Paths.get("custom system"), Paths.get("custom config"), Paths.get("custom system/log"))
    val pluginsPath = dirs.config.resolve("plugins")
    val builder = vmOptionsBuilder.copy(testRuntimeDirectories = Some(dirs))
    val withStalePaths = vmOptions.withExtraOptions(Seq(
      "-Didea.system.path=stale", "-Didea.config.path=stale", "-Didea.log.path=stale", "-Dother.option=kept"))
    val actual = builder.build(withStalePaths, forTests = true, quoteValues = false)
    actual should contain(s"-Didea.plugins.path=$pluginsPath")
    actual.filter(IntellijTestRuntimeDirectories.isIntellijDirectoryOption) shouldBe dirs.jvmOptions
    actual should contain("-Dother.option=kept")
    actual should contain("-Dkey1=value")
    actual.filterNot(option => IntellijTestRuntimeDirectories.isIntellijDirectoryOption(option) ||
      option.startsWith("-Didea.plugins.path=")) shouldBe
      vmOptionsBuilder.build(withStalePaths, forTests = true, quoteValues = false)
        .filterNot(option => IntellijTestRuntimeDirectories.isIntellijDirectoryOption(option) ||
          option.startsWith("-Didea.plugins.path="))
    builder.buildQuotedNoEscapeXml(withStalePaths, forTests = true) should contain("-Didea.system.path=\"custom system\"")
    builder.buildQuotedNoEscapeXml(withStalePaths, forTests = true) should contain(s"""-Didea.plugins.path="$pluginsPath"""")
    builder.build(withStalePaths, forTests = true, quoteValues = true) should contain("-Didea.config.path=&quot;custom config&quot;")
    builder.build(withStalePaths, forTests = true, quoteValues = true) should contain(s"-Didea.plugins.path=&quot;$pluginsPath&quot;")
    builder.build(vmOptions, forTests = false, quoteValues = false) shouldBe
      vmOptionsBuilder.build(vmOptions, forTests = false, quoteValues = false)
  }

  test("test plugin discovery follows the test config while development keeps SDK plugins") {
    vmOptionsBuilder.build(vmOptions, forTests = true, quoteValues = false) should contain(
      s"-Didea.plugins.path=${Paths.get("bar baz", "test-config", "plugins")}")
    vmOptionsBuilder.withCustomSystemAndConfigDir("custom").build(vmOptions, forTests = true, quoteValues = false) should contain(
      s"-Didea.plugins.path=${Paths.get("bar baz", "custom-config", "plugins")}")
    vmOptionsBuilder.build(vmOptions, forTests = false, quoteValues = false) should contain(
      s"-Didea.plugins.path=${mockIntellijBaseDir.resolve("custom-plugins")}")
  }

  private def assertRuntimeDirectories(purpose: String): Unit = {
    val dirs = CheckoutRuntimeDirectories(Paths.get("plugin home"), new File("/checkout"))
    val selected = if (purpose == "dev") dirs.dev("IU-263.12345.67") else dirs.buildIndex("IU-263.12345.67")
    val builder = vmOptionsBuilder.withRuntimeDirectories(selected)
    val conflicting = vmOptions.withExtraOptions(Seq(
      "-Didea.system.path=stale", "-Didea.config.path=stale", "-Didea.log.path=stale", "-Dretained=yes"))
    val actual = builder.build(conflicting, forTests = false, quoteValues = false)
    actual.filter(IntellijTestRuntimeDirectories.isIntellijDirectoryOption) shouldBe selected.jvmOptions
    actual should contain("-Dretained=yes")
    actual should contain("-Dplugin.path=foo bar")
    actual should contain(s"-Didea.plugins.path=${mockIntellijBaseDir.resolve("custom-plugins")}")
    builder.buildQuotedNoEscapeXml(conflicting, forTests = false) should contain(
      s"""-Didea.config.path="${selected.config}"""")
  }
}
