package org.jetbrains.sbtidea.download

import java.io.OutputStreamWriter
import java.nio.file.Files

import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea.packaging.artifact
import org.jetbrains.sbtidea.{CapturingLogger, ConsoleLogger}
import org.scalatest.{FunSuite, Matchers}

class LocalPluginRegistryTest extends FunSuite with Matchers with IdeaMock with ConsoleLogger {

  private val ideaRoot = installIdeaMock

  private val pluginsFolder = ideaRoot / "plugins"
  private val pluginsIndex  = ideaRoot / "plugins.idx"

  test("LocalPluginRegistry builds bundled plugin index") {
    val registry = new LocalPluginRegistry(ideaRoot, log)
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
    val registry      = new LocalPluginRegistry(ideaRoot, log)
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
    val capturingLog = new CapturingLogger
    val registry  = new LocalPluginRegistry(ideaRoot, capturingLog)
    val newPlugin = "org.jetbrains.hocon".toPlugin
    assertThrows[RuntimeException](registry.getInstalledPluginRoot(newPlugin))
  }

  test("Plugin descriptor not extracted from invalid plugins") {
    LocalPluginRegistry.extractInstalledPluginDescriptor(ideaRoot) shouldBe 'left
  }

  test("LocalPluginRegistry saves and restores plugin index") {
    val oldRegistry = new LocalPluginRegistry(ideaRoot, log)
    val newPlugin = "org.jetbrains.hocon".toPlugin
    val newPluginRoot = pluginsFolder / "hocon"
    oldRegistry.markPluginInstalled(newPlugin, newPluginRoot)

    val newRegistry = new LocalPluginRegistry(ideaRoot, log)
    newRegistry.isPluginInstalled(newPlugin) shouldBe true

    Files.delete(pluginsIndex)
  }

  test("LocalPluginRegistry should handle corrupt index") {
    val oldRegistry = new LocalPluginRegistry(ideaRoot, log)
    val newPlugin = "org.jetbrains.hocon".toPlugin
    val newPluginRoot = pluginsFolder / "hocon"
    oldRegistry.markPluginInstalled(newPlugin, newPluginRoot)

    artifact.using(new OutputStreamWriter(Files.newOutputStream(pluginsIndex))) { writer =>
      writer.write(0xff)
    }

    val capturingLog = new CapturingLogger
    val newRegistry = new LocalPluginRegistry(ideaRoot, capturingLog)

    newRegistry.isPluginInstalled(newPlugin) shouldBe false
    capturingLog.messages should contain ("Failed to load local plugin index: java.io.EOFException")
    pluginsIndex.toFile.exists() shouldBe false
  }

}
