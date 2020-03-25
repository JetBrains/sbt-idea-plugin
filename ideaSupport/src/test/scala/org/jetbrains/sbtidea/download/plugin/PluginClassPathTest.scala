package org.jetbrains.sbtidea.download.plugin

import java.nio.file.Files

import org.jetbrains.sbtidea.download.plugin.LocalPluginRegistry.MissingPluginRootException
import org.jetbrains.sbtidea.tasks.CreatePluginsClasspath
import org.jetbrains.sbtidea.pathToPathExt
import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.download.idea.IdeaMock
import sbt._

class PluginClassPathTest extends IntellijPluginInstallerTestBase with IdeaMock {

  test("plugin classpath contains all necessary jars") {
    val installer = createInstaller()
    val pluginZipMetadata = PluginDescriptor("org.intellij.scala", "Scala", "2019.3.1", "193.0", "194.0")
    val mockPluginZipDist = createPluginZipMock(pluginZipMetadata)
    val pluginJarMetadata = PluginDescriptor("org.jetbrains.plugins.hocon", "HOCON", "0.0.1", "193.0", "194.0")
    val mockPluginJarDist = createPluginJarMock(pluginJarMetadata)
    installer.installIdeaPlugin(pluginZipMetadata.toPluginId, mockPluginZipDist)
    installer.installIdeaPlugin(pluginJarMetadata.toPluginId, mockPluginJarDist)

    val classpath =
      CreatePluginsClasspath(ideaRoot,
        IDEA_BUILDINFO,
        Seq("com.intellij.properties".toPlugin,
            "org.jetbrains.plugins.yaml".toPlugin,
            pluginJarMetadata.toPluginId,
            pluginZipMetadata.toPluginId),
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
      CreatePluginsClasspath(ideaRoot,
        IDEA_BUILDINFO,
        Seq("com.intellij.properties".toPlugin,
            "org.jetbrains.plugins.yaml".toPlugin),
        log)

    classpath.map(_.data.getName) should not contain (wrongJar)
  }


}
