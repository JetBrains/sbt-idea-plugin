package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.CapturingLogger.captureLog
import org.jetbrains.sbtidea.Keys.String2Plugin

final class IntellijPluginInstallerTest extends IntellijPluginInstallerTestBase {

  test("Plugin installer reports non-installed plugins") {
    val fakePlugin = "org.myFake.plugin:0.999:trunk".toPlugin
    val installer = createInstaller
    installer.isInstalled(fakePlugin) shouldBe false
  }

  test("Plugin installer installs zip artifact") {
    val pluginMetadata = PluginDescriptor("org.intellij.scala", "JetBrains", "Scala", "2024.2.1", "242.0", "242.0")
    val mockPluginDist = createPluginZipMock(pluginMetadata)
    val installer = createInstaller
    val pluginRoot = installer.installIdeaPlugin(pluginMetadata.toPluginId, mockPluginDist)
    pluginRoot.toFile.exists() shouldBe true
    LocalPluginRegistry.extractPluginMetaData(pluginRoot) shouldBe 'right
  }

  test("Plugin installer installs jar artifact") {
    val pluginMetadata = PluginDescriptor("org.intellij.scala", "JetBrains", "Scala", "2024.2.1", "242.0", "242.0")
    val mockPluginDist = createPluginJarMock(pluginMetadata)
    val installer = createInstaller
    val pluginRoot = installer.installIdeaPlugin(pluginMetadata.toPluginId, mockPluginDist)
    pluginRoot.toFile.exists() shouldBe true
    LocalPluginRegistry.extractPluginMetaData(pluginRoot) shouldBe 'right
  }

  test("Plugin installer checks IDEA compatibility") {
    val pluginMetadata = PluginDescriptor("org.intellij.scala", "JetBrains", "Scala", "2026.1.679", "261.123", "261.4")
    val mockPluginDist = createPluginJarMock(pluginMetadata)
    val installer = createInstaller
    installer.installIdeaPlugin(pluginMetadata.toPluginId, mockPluginDist)
    val messages = captureLog(installer.isInstalled(pluginMetadata.toPluginId) shouldBe false)
    messages should contain ("[warn] Plugin org.intellij.scala is incompatible with current ideaVersion(261.22158.277): PluginDescriptor(org.intellij.scala,JetBrains,Scala,2026.1.679,261.123,261.4,List())")
  }

  test("Plugin installer checks IDEA compatibility using wildcards") {
    val pluginMetadata = PluginDescriptor("org.intellij.scala", "JetBrains", "Scala", "2026.1.679", "261.0", "261.*")
    val mockPluginDist = createPluginJarMock(pluginMetadata)
    val installer = createInstaller
    installer.installIdeaPlugin(pluginMetadata.toPluginId, mockPluginDist)
    val messages = captureLog(installer.isInstalled(pluginMetadata.toPluginId) shouldBe true)
    messages should not contain
      "Plugin org.intellij.scala is incompatible with current ideaVersion(261.22158.277): PluginDescriptor(org.intellij.scala,JetBrains,Scala,2026.1.679,261.0,261.*,List())"
  }


  test("Plugin installer checks for newer plugin version") {
    val pluginMetadata = PluginDescriptor("org.intellij.scala", "JetBrains", "Scala", "2024.1.1", "261.0", "261.*")
    val mockPluginDist = createPluginJarMock(pluginMetadata)
    val installer = createInstaller
    val pluginId = pluginMetadata.toPluginId.copy(version = None)
    installer.installIdeaPlugin(pluginId, mockPluginDist)

    val messages = captureLog(installer.isInstalled(pluginId) shouldBe false)

    messages.exists(_.startsWith("[warn] Newer version of plugin org.intellij.scala is available:")) shouldBe true
  }

}
