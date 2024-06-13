package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.ConsoleLogger
import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.jetbrains.sbtidea.tasks.classpath.PluginClasspathUtils
import sbt.*

import java.nio.file.Files

class PluginClassPathTest extends IntellijPluginInstallerTestBase with IdeaMock {

  test("plugin classpath contains all necessary jars") {
    val installer = createInstaller()
    val pluginZipMetadata = PluginDescriptor("org.intellij.scala", "JetBrains", "Scala", "2019.3.1", "193.0", "194.0")
    val mockPluginZipDist = createPluginZipMock(pluginZipMetadata)
    val pluginJarMetadata = PluginDescriptor("org.jetbrains.plugins.hocon", "JetBrains", "HOCON", "0.0.1", "193.0", "194.0")
    val mockPluginJarDist = createPluginJarMock(pluginJarMetadata)
    installer.installIdeaPlugin(pluginZipMetadata.toPluginId, mockPluginZipDist)
    installer.installIdeaPlugin(pluginJarMetadata.toPluginId, mockPluginJarDist)

    val classpath =
      PluginClasspathUtils.buildPluginJars(
        ideaRoot,
        IDEA_BUILDINFO,
        Seq("com.intellij.properties".toPlugin,
            "org.jetbrains.plugins.yaml".toPlugin,
            pluginJarMetadata.toPluginId,
            pluginZipMetadata.toPluginId),
        new ConsoleLogger,
      ).flatMap(_.pluginJars)

    classpath.map(_.getName) should contain allElementsOf Seq("HOCON.jar", "yaml.jar", "Scala.jar", "properties.jar")
  }

  test("plugin classpath doesn't contain jars other than from 'lib'") {
    val stuffPath = pluginsRoot / "properties" / "lib" / "stuff"
    val wrongJar = "wrong.jar"
    println(stuffPath.toFile.getParentFile.exists())
    println(stuffPath.toFile.exists())
    Files.createDirectory(stuffPath)
    Files.createFile(stuffPath / wrongJar)
    Files.createFile(pluginsRoot / wrongJar)

    val classpath =
      PluginClasspathUtils.buildPluginJars(
        ideaRoot,
        IDEA_BUILDINFO,
        Seq("com.intellij.properties".toPlugin,
            "org.jetbrains.plugins.yaml".toPlugin),
        new ConsoleLogger,
      ).flatMap(_.pluginJars)

    classpath.map(_.getName) should not contain wrongJar
  }
}
