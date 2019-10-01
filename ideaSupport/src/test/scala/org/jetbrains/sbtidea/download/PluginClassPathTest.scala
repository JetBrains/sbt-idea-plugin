package org.jetbrains.sbtidea.download

import java.nio.file.Files

import org.jetbrains.sbtidea.download.LocalPluginRegistry.MissingPluginRootException
import org.jetbrains.sbtidea.download.api.PluginMetadata
import org.jetbrains.sbtidea.tasks.CreatePluginsClasspath

class PluginClassPathTest extends IntellijPluginInstallerTestBase {

  test("plugin classpath contains all necessary jars") {
    val installer = createInstaller()
    val pluginZipMetadata = PluginMetadata("org.intellij.scala", "Scala", "2019.3.1", "193.0", "194.0")
    val mockPluginZipDist = createPluginZipMock(pluginZipMetadata)
    val pluginJarMetadata = PluginMetadata("org.jetbrains.plugins.hocon", "HOCON", "0.0.1", "193.0", "194.0")
    val mockPluginJarDist = createPluginJarMock(pluginJarMetadata)
    installer.installIdeaPlugin(pluginZipMetadata.toPluginId, mockPluginZipDist)
    installer.installIdeaPlugin(pluginJarMetadata.toPluginId, mockPluginJarDist)

    val classpath =
      CreatePluginsClasspath(pluginsRoot.toFile,
        Seq("yaml", "properties"),
        Seq(pluginJarMetadata.toPluginId, pluginZipMetadata.toPluginId),
        log)

    classpath.map(_.data.getName) should contain allElementsOf Seq("HOCON.jar", "yaml.jar", "Scala.jar", "properties.jar")
  }

  test("plugin classpath doesn't contain jars other than from 'lib'") {
    val stuffPath = pluginsRoot / "properties" / "lib" / "stuff"
    val wrongJar = "wrong.jar"
    Files.createDirectory(stuffPath)
    Files.createFile(stuffPath / wrongJar)
    Files.createFile(pluginsRoot / wrongJar)

    val classpath =
      CreatePluginsClasspath(pluginsRoot.toFile,
        Seq("yaml", "properties"),
        Seq(),
        log)

    classpath.map(_.data.getName) should not contain (wrongJar)
  }

  test("plugin classpath building is aborted when non-existent plugin is passed") {
    assertThrows[MissingPluginRootException] {
      CreatePluginsClasspath(pluginsRoot.toFile,
        Seq("yaml", "properties", "INVALID"),
        Seq(),
        log)
    }
  }

}
