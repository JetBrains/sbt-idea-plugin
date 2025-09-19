package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.IntelliJPlatform.IdeaCommunity
import org.jetbrains.sbtidea.productInfo.{Launch, OS, ProductInfoExtraDataProvider}
import org.jetbrains.sbtidea.runIdea.CustomIntellijVMOptions.DebugInfo
import org.jetbrains.sbtidea.runIdea.IntellijVMOptionsBuilder.VmOptions
import org.jetbrains.sbtidea.runIdea.{CustomIntellijVMOptions, IntellijVMOptions, IntellijVMOptionsBuilder}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.{Path, Paths}
import scala.annotation.nowarn

class IdeaVmOptionsTest extends AnyFunSuite with Matchers {

  private val extraVmOptions = Seq("-Dkey1=value", "-Dkey2=\"value\"")

  private val mockIntellijBaseDir: Path = Paths.get("test non-existent intellijDirectory")

  private val oldVmOptions = VmOptions.Old(IntellijVMOptions(
    platform = IdeaCommunity,
    pluginPath = Paths.get("foo bar"),
    ideaHome = Paths.get("bar baz"),
    intellijDirectory = mockIntellijBaseDir
  ).withOptions(extraVmOptions): @nowarn("cat=deprecation"))

  private val newVmOptions = VmOptions.New(CustomIntellijVMOptions(
    xmx = Some(1024),
    xms = Some(128),
    debugInfo = Some(DebugInfo.Default),
    extraOptions = extraVmOptions
  ))

  private val mockLaunch = Launch(
    OS.Linux,
    arch = "amd64",
    launcherPath = "bin/idea.sh",
    javaExecutablePath = None,
    vmOptionsFilePath = "bin/linux/idea64.vmoptions",
    startupWmClass = Some("jetbrains-idea"),
    bootClassPathJarNames = List("platform-loader.jar", "util-8.jar", "util.jar", "app-client.jar", "util_rt.jar", "product.jar", "opentelemetry.jar", "app.jar", "product-client.jar", "lib-client.jar", "stats.jar", "jps-model.jar", "external-system-rt.jar", "rd.jar", "bouncy-castle.jar", "protobuf.jar", "intellij-test-discovery.jar", "forms_rt.jar", "lib.jar", "externalProcess-rt.jar", "groovy.jar", "annotations.jar", "idea_rt.jar", "intellij-coverage-agent-1.0.750.jar", "jsch-agent.jar", "junit4.jar", "nio-fs.jar", "trove.jar"),
    additionalJvmArguments = List("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader", "-Didea.vendor.name=JetBrains", "-Didea.paths.selector=IntelliJIdea2024.2", "-Djna.boot.library.path=$IDE_HOME/lib/jna/amd64", "-Dpty4j.preferred.native.folder=$IDE_HOME/lib/pty4j", "-Djna.nosys=true", "-Djna.noclasspath=true", "-Dintellij.platform.runtime.repository.path=$IDE_HOME/modules/module-descriptors.jar", "-Dsplash=true", "-Daether.connector.resumeDownloads=false", "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.lang.ref=ALL-UNNAMED", "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED", "--add-opens=java.base/java.nio=ALL-UNNAMED", "--add-opens=java.base/java.nio.charset=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED", "--add-opens=java.base/java.time=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED", "--add-opens=java.base/sun.net.dns=ALL-UNNAMED", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED", "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED", "--add-opens=java.base/sun.security.util=ALL-UNNAMED", "--add-opens=java.desktop/com.sun.java.swing=ALL-UNNAMED", "--add-opens=java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED", "--add-opens=java.desktop/java.awt=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED", "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED", "--add-opens=java.desktop/sun.font=ALL-UNNAMED", "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED", "--add-opens=java.desktop/sun.swing=ALL-UNNAMED", "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED", "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED", "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED", "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"),
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

  private def vmOptionsBuilder(useNewVmOptions: Boolean) = {
    new IntellijVMOptionsBuilder(
      platform = IdeaCommunity,
      productInfoExtraDataProvider = mockProductInfoExtraDataProvider,
      pluginPath = Paths.get("foo bar"),
      ideaHome = Paths.get("bar baz"),
      intellijDirectory = mockIntellijBaseDir,
    )
  }

  private val vmOptionsBuilderNew = vmOptionsBuilder(true)
  private val vmOptionsBuilderLegacy = vmOptionsBuilder(false)

  private val Q = "&quot;"

  private val mustBeQuoted = Seq(
    "plugin.path",
    "idea.system.path",
    "idea.config.path"
  )

  test("paths are quoted in xml version") {
    val asSeq = vmOptionsBuilderNew.build(newVmOptions, forTests = false, quoteValues = true)
    val quotedKeys = asSeq.filter(s => mustBeQuoted.exists(s.contains(_)))
    quotedKeys.size shouldBe mustBeQuoted.size
    quotedKeys.foreach { option =>
      option should endWith regex s"=$Q.+$Q$$"
    }
  }

  test("paths are NOT quoted in SBT version") {
    val asSeq = vmOptionsBuilderNew.build(newVmOptions, forTests = false, quoteValues = false)
    val quotedKeys = asSeq.filter(s => mustBeQuoted.exists(s.contains(_)))
    quotedKeys.size shouldBe mustBeQuoted.size
    quotedKeys.foreach { option =>
      option shouldNot endWith regex s"=$Q.+$Q$$"
    }
  }

  test("paths are quoted in xml version (legacy)") {
    val asSeq = vmOptionsBuilderLegacy.build(oldVmOptions, forTests = false, quoteValues = true)
    val quotedKeys = asSeq.filter(s => mustBeQuoted.exists(s.contains(_)))
    quotedKeys.size shouldBe mustBeQuoted.size
    quotedKeys.foreach { option =>
      option should endWith regex s"=$Q.+$Q$$"
    }
  }

  test("paths are NOT quoted in SBT version (legacy)") {
    val asSeq = vmOptionsBuilderLegacy.build(oldVmOptions, forTests = false, quoteValues = false)
    val quotedKeys = asSeq.filter(s => mustBeQuoted.exists(s.contains(_)))
    quotedKeys.size shouldBe mustBeQuoted.size
    quotedKeys.foreach { option =>
      option shouldNot endWith regex s"=$Q.+$Q$$"
    }
  }
}
