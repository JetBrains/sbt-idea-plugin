package org.jetbrains.sbtidea.download.plugin

import java.io.OutputStreamWriter
import java.nio.file.{FileSystems, Files}

import org.jetbrains.sbtidea.CapturingLogger.captureLog
import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea.download.NioUtils
import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.jetbrains.sbtidea.packaging.artifact
import org.jetbrains.sbtidea.{ConsoleLogger, packaging, pathToPathExt}
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import sbt._

import scala.collection.JavaConverters.mapAsJavaMapConverter

final class LocalPluginRegistryTest extends FunSuite with Matchers with IdeaMock with PluginMock with ConsoleLogger with BeforeAndAfter {

  private val ideaRoot = installIdeaMock

  private val pluginsFolder = ideaRoot / "plugins"
  private val pluginsIndex  = ideaRoot / PluginIndexImpl.INDEX_FILENAME

  private val hoconXML =
    """
      |<!DOCTYPE idea-plugin PUBLIC "Plugin/DTD" "https://plugins.jetbrains.com/plugin.dtd">
      |<idea-plugin>
      |    <id>org.jetbrains.plugins.hocon</id>
      |    <name>HOCON</name>
      |    <description>Standalone HOCON plugin for IntelliJ IDEA</description>
      |    <version>2020.1.99-SNAPSHOT</version>
      |    <vendor>Roman Janusz, AVSystem, JetBrains</vendor>
      |    <idea-version since-build="201.0" until-build="220.0"/>
      |    <depends>com.intellij.modules.platform</depends>
      |    <depends>com.intellij.modules.lang</depends>
      |    <depends optional="true" config-file="hocon-java.xml">com.intellij.modules.java</depends>
      |</idea-plugin>
      |""".stripMargin

  private def usingFakePlugin[T](pluginName: String, pluginXml: String)(f: => T): T = {
    val options = Map("create" -> "true").asJava
    val pluginJar = pluginsFolder / pluginName / "lib" / s"$pluginName.jar"
    Files.createDirectories(pluginJar.getParent)
    val jarUri = new URI("jar:file:" + pluginJar.toString)
    packaging.artifact.using(FileSystems.newFileSystem(jarUri, options)) {fs =>
      val jarXml = fs.getPath("META-INF", "plugin.xml")
      Files.createDirectories(jarXml.getParent)
      Files.write(jarXml, pluginXml.getBytes())
    }
    val result = f
    NioUtils.delete(pluginsFolder / pluginName)
    result
  }

  before {
    pluginsIndex.toFile.delete()
  }

  test("LocalPluginRegistry builds bundled plugin index") {
    val registry = new LocalPluginRegistry(ideaRoot)
    for (plugin <- bundledPlugins) {
      withClue(s"checking $plugin")
        { registry.isPluginInstalled(plugin) shouldBe true }
    }
  }

  test("LocalPluginRegistry extracts plugin descriptor from ") {
    for (root <- pluginsFolder.list) {
      withClue(s"checking $root")
        { LocalPluginRegistry.extractInstalledPluginDescriptor(root) shouldBe 'right }
    }
  }

  test("LocalPluginRegistry detects plugin roots") {
    val registry      = new LocalPluginRegistry(ideaRoot)
    val actualRoots   = pluginsFolder.list
    val detectedRoots =
      for {plugin <- bundledPlugins}
        yield registry.getInstalledPluginRoot(plugin)

    detectedRoots should contain allElementsOf actualRoots
  }


  test("LocalPluginRegistry fails when no descriptor was found") {
    val fakePluginRoot = pluginsFolder / "fakePlugin"
    try {
      Files.createDirectory(fakePluginRoot)
      LocalPluginRegistry.extractInstalledPluginDescriptor(fakePluginRoot) shouldBe 'left
    } finally {
      Files.deleteIfExists(fakePluginRoot)
    }
  }

  test("LocalPluginRegistry reports error when plugin is not in the index") {
    val registry  = new LocalPluginRegistry(ideaRoot)
    val newPlugin = "org.jetbrains.hocon".toPlugin
    assertThrows[RuntimeException](registry.getInstalledPluginRoot(newPlugin))
  }

  test("Plugin descriptor not extracted from invalid plugins") {
    LocalPluginRegistry.extractInstalledPluginDescriptor(ideaRoot) shouldBe 'left
  }

  test("LocalPluginRegistry saves and restores plugin index") {
    val oldRegistry = new LocalPluginRegistry(ideaRoot)
    val newPlugin = "org.jetbrains.plugins.hocon".toPlugin
    val newPluginRoot = pluginsFolder / "hocon"
    usingFakePlugin("hocon", hoconXML) {
      oldRegistry.markPluginInstalled(newPlugin, newPluginRoot)

      val newRegistry = new LocalPluginRegistry(ideaRoot)
      newRegistry.isPluginInstalled(newPlugin) shouldBe true

      Files.delete(pluginsIndex)
    }
  }

  test("LocalPluginRegistry should handle corrupt index") {
    val oldRegistry = LocalPluginRegistry.instanceFor(ideaRoot)
    val newPlugin = "org.jetbrains.plugins.hocon".toPlugin
    val newPluginRoot = pluginsFolder / "hocon"
    usingFakePlugin("hocon", hoconXML) {
      oldRegistry.markPluginInstalled(newPlugin, newPluginRoot)

      artifact.using(new OutputStreamWriter(Files.newOutputStream(pluginsIndex))) { writer =>
        writer.write(0xff)
      }

      val newRegistry = new LocalPluginRegistry(ideaRoot)

      val messages = captureLog {
        newRegistry.isPluginInstalled(newPlugin) shouldBe false
      }
      messages should contain("Failed to load plugin index from disk: java.io.EOFException")
      pluginsIndex.toFile.exists() shouldBe false
    }
  }

  test("irrelevant files and folders are ignored when building index") {
    Files.createDirectory(pluginsFolder / "NON-PLUGIN")
    Files.createFile( pluginsFolder / ".DS_Store")

    val registry  = new LocalPluginRegistry(ideaRoot)

    val messages = captureLog {
      registry.isPluginInstalled(bundledPlugins.head) shouldBe true
    }

    messages.exists(_.matches("Failed to add plugin to index: Couldn't find plugin.xml in .+\\.DS_Store")) shouldBe true
    messages.exists(_.matches("Failed to add plugin to index: Plugin root .+NON-PLUGIN has no lib directory")) shouldBe true
  }

}