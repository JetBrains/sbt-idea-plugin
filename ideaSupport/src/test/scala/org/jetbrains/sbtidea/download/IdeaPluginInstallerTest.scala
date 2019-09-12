package org.jetbrains.sbtidea.download

import java.nio.file._

import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea.{CapturingLogger, ConsoleLogger, PluginLogger}
import org.scalatest.{FunSuite, Matchers}
import org.jetbrains.sbtidea.Keys.IdeaEdition
import org.jetbrains.sbtidea.download.api.PluginMetadata

class IdeaPluginInstallerTest extends FunSuite with Matchers with IdeaMock with PluginMock with ConsoleLogger {

  private val ideaRoot            = installIdeaMock
  private val ideaBuild           = BuildInfo(IDEA_VERSION, IdeaEdition.Ultimate)

  private def copyPluginDist(fileName: String): Path = {
    val tmpDir = Files.createTempDirectory(getClass.getSimpleName)
    val from = Paths.get(getClass.getResource(fileName).toURI)
    val to   = tmpDir.resolve(fileName)
    Files.copy(from, to)
    to
  }

  private def createInstaller(logger: PluginLogger = log): IdeaPluginInstaller = new IdeaPluginInstaller {
    override protected def buildInfo: BuildInfo = ideaBuild
    override protected def log: PluginLogger = logger
    override def getInstallDir: Path = ideaRoot
    override def isIdeaAlreadyInstalled: Boolean = true
    override def installIdeaDist(files: Seq[(ArtifactPart, Path)]): Path = ideaRoot
  }

  test("Plugin installer reports non-installed plugins") {
    val fakePlugin = "org.myFake.plugin:0.999:trunk".toPlugin
    val installer = createInstaller()
    installer.isPluginAlreadyInstalledAndUpdated(fakePlugin) shouldBe false
  }

  test("Plugin installer installs zip artifact") {
    val pluginMetadata = PluginMetadata("org.intellij.scala", "Scala", "2019.3.1", "193.0", "194.0")
    val mockPluginDist = createPluginZipMock(pluginMetadata)
    val installer = createInstaller()
    val pluginRoot = installer.installIdeaPlugin(pluginMetadata.toPluginId, mockPluginDist)
    pluginRoot.toFile.exists() shouldBe true
    LocalPluginRegistry.extractInstalledPluginDescriptor(pluginRoot) shouldBe 'right
    NioUtils.delete(pluginRoot)
    NioUtils.delete(ideaRoot / "plugins.idx")
  }

  test("Plugin installer installs jar artifact") {
    val pluginMetadata = PluginMetadata("org.intellij.scala", "Scala", "2019.3.1", "193.0", "194.0")
    val mockPluginDist = createPluginJarMock(pluginMetadata)
    val installer = createInstaller()
    val pluginRoot = installer.installIdeaPlugin(pluginMetadata.toPluginId, mockPluginDist)
    pluginRoot.toFile.exists() shouldBe true
    LocalPluginRegistry.extractInstalledPluginDescriptor(pluginRoot) shouldBe 'right
    NioUtils.delete(pluginRoot)
    NioUtils.delete(ideaRoot / "plugins.idx")
  }

  test("Plugin installer checks IDEA compatibility") {
    val capturingLogger = new CapturingLogger
    val pluginMetadata = PluginMetadata("org.intellij.scala", "Scala", "2019.2.423", "193.123", "193.4")
    val mockPluginDist = createPluginJarMock(pluginMetadata)
    val installer = createInstaller(capturingLogger)
    val installedPluginRoot = installer.installIdeaPlugin(pluginMetadata.toPluginId, mockPluginDist)
    installer.isPluginAlreadyInstalledAndUpdated(pluginMetadata.toPluginId) shouldBe false
    capturingLogger.messages should contain (
      "Plugin org.intellij.scala is incompatible with current ideaVersion(192.5728.12): PluginMetadata(org.intellij.scala,Scala,2019.2.423,193.123,193.4)"
    )
    NioUtils.delete(installedPluginRoot)
    NioUtils.delete(ideaRoot / "plugins.idx")
  }

  test("Plugin installer checks for newer plugin version") {
    val capturingLogger = new CapturingLogger
    val pluginMetadata = PluginMetadata("org.intellij.scala", "Scala", "2019.2.1", "192.123", "193.4")
    val mockPluginDist = createPluginJarMock(pluginMetadata)
    val installer = createInstaller(capturingLogger)
    val pluginId = pluginMetadata.toPluginId.copy(version = None)
    val installedPluginRoot = installer.installIdeaPlugin(pluginId, mockPluginDist)

    installer.isPluginAlreadyInstalledAndUpdated(pluginId) shouldBe false
    capturingLogger.messages should contain (
      "Newer version of plugin org.intellij.scala is available: 2019.2.1 -> 2019.2.23"
    )
    NioUtils.delete(installedPluginRoot)
    NioUtils.delete(ideaRoot / "plugins.idx")
  }

}
