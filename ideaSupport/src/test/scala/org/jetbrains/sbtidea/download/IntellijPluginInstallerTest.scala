package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.CapturingLogger
import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea.download.api.PluginMetadata
import org.jetbrains.sbtidea.PathExt

final class IntellijPluginInstallerTest extends IntellijPluginInstallerTestBase {

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

  test("Plugin installer checks IDEA compatibility using wildcards") {
    val capturingLogger = new CapturingLogger
    val pluginMetadata = PluginMetadata("org.intellij.scala", "Scala", "2019.2.423", "192.123", "193.*")
    val mockPluginDist = createPluginJarMock(pluginMetadata)
    val installer = createInstaller(capturingLogger)
    val installedPluginRoot = installer.installIdeaPlugin(pluginMetadata.toPluginId, mockPluginDist)
    installer.isPluginAlreadyInstalledAndUpdated(pluginMetadata.toPluginId) shouldBe true
    capturingLogger.messages should not contain (
      "Plugin org.intellij.scala is incompatible with current ideaVersion(192.5728.12): PluginMetadata(org.intellij.scala,Scala,2019.2.423,193.123,193.*)"
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
    capturingLogger.messages.exists(_.startsWith("Newer version of plugin org.intellij.scala is available:")) shouldBe true
    NioUtils.delete(installedPluginRoot)
    NioUtils.delete(ideaRoot / "plugins.idx")
  }

}
