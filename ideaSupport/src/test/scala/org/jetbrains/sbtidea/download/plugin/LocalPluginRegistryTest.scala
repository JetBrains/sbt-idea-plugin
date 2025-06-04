package org.jetbrains.sbtidea.download.plugin

import org.apache.commons.io.FileUtils
import org.jetbrains.sbtidea.CapturingLogger.captureLog
import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea.PathExt
import org.jetbrains.sbtidea.download.NioUtils
import org.jetbrains.sbtidea.download.api.IdeInstallationProcessContext
import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfter, Inspectors}
import sbt.*

import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.{FileSystems, Files}
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Using

final class LocalPluginRegistryTest
  extends AnyFunSuite
    with Matchers
    with Inspectors
    with IdeaMock
    with PluginMock
    with BeforeAndAfter {

  private val ideaRoot = installIdeaMock
  protected val installContext: IdeInstallationProcessContext = new IdeInstallationProcessContext(ideaRoot, ideaRoot.getParent)

  private def newLocalPluginRegistry: LocalPluginRegistry = new LocalPluginRegistry(installContext)

  private val pluginsFolder = ideaRoot / "plugins"
  private val pluginsIndex = ideaRoot / PluginIndexImpl.PluginsIndexFilename

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
    val isWindows = System.getProperty("os.name").toLowerCase.contains("win")
    val jarUri = new URI(s"jar:file:${if (isWindows) "/" else ""}" + pluginJar.toString.replace("\\", "/"))
    Using.resource(FileSystems.newFileSystem(jarUri, options)) { fs =>
      val jarXml = fs.getPath("META-INF", "plugin.xml")
      Files.createDirectories(jarXml.getParent)
      Files.write(jarXml, pluginXml.getBytes())
    }
    val result = f
    NioUtils.delete(pluginsFolder / pluginName)
    result
  }

  before {
    val pluginIndexFile = pluginsIndex.toFile
    if (pluginIndexFile.exists() && !pluginIndexFile.delete()) {
      System.err.println(s"Can't delete plugin index file before test: $pluginIndexFile")
    }
  }

  test("LocalPluginRegistry builds bundled plugin index") {
    val registry = newLocalPluginRegistry

    for (plugin <- bundledPlugins) {
      withClue(s"checking $plugin") {
        registry.isPluginInstalled(plugin) shouldBe true
      }
    }
  }

  test("LocalPluginRegistry extracts plugin descriptor from ") {
    for (root <- pluginsFolder.list) {
      withClue(s"checking $root") {
        LocalPluginRegistry.extractPluginMetaData(root) shouldBe 'right
      }
    }
  }

  test("LocalPluginRegistry extracts and merges plugin descriptor from from plugin.xml and pluginBase.xml") {
    val expectedDescriptor = PluginDescriptor(
      "com.jetbrains.codeWithMe",
      "JetBrains",
      "Code With Me",
      "232.3318",
      "232.3318",
      "232.3318",
      List(
        PluginDescriptor.Dependency("com.intellij.modules.platform", optional = false),
        PluginDescriptor.Dependency("com.jetbrains.projector.libs", optional = false),
        PluginDescriptor.Dependency("org.jetbrains.plugins.terminal", optional = true),
        PluginDescriptor.Dependency("com.intellij.java", optional = true),
        PluginDescriptor.Dependency("Pythonid", optional = true),
      )
    )

    val actualDescriptor = LocalPluginRegistry.extractPluginMetaData(pluginsFolder.resolve("cwm-plugin"))
    actualDescriptor shouldBe Right(expectedDescriptor)
  }

  test("LocalPluginRegistry detects plugin roots") {
    val registry = newLocalPluginRegistry
    val actualRoots = pluginsFolder.list
    val detectedRoots =
      for {plugin <- bundledPlugins}
        yield registry.getInstalledPluginRoot(plugin)

    detectedRoots should contain allElementsOf actualRoots
  }

  test("LocalPluginRegistry fails when no descriptor was found") {
    val fakePluginRoot = pluginsFolder / "fakePlugin"
    try {
      Files.createDirectory(fakePluginRoot)
      LocalPluginRegistry.extractPluginMetaData(fakePluginRoot) shouldBe 'left
    } finally {
      Files.deleteIfExists(fakePluginRoot)
    }
  }

  test("LocalPluginRegistry reports error when plugin is not in the index") {
    val registry = newLocalPluginRegistry
    val newPlugin = "org.jetbrains.hocon".toPlugin
    assertThrows[RuntimeException](registry.getInstalledPluginRoot(newPlugin))
  }

  test("Plugin descriptor not extracted from invalid plugins") {
    LocalPluginRegistry.extractPluginMetaData(ideaRoot) shouldBe 'left
  }

  test("LocalPluginRegistry saves and restores plugin index") {
    val oldRegistry = newLocalPluginRegistry
    val newPlugin = "org.jetbrains.plugins.hocon".toPlugin
    val newPluginRoot = pluginsFolder / "hocon"
    usingFakePlugin("hocon", hoconXML) {
      oldRegistry.markPluginInstalled(newPlugin, newPluginRoot)

      val newRegistry = newLocalPluginRegistry
      newRegistry.isPluginInstalled(newPlugin) shouldBe true

      Files.delete(pluginsIndex)
    }
  }

  test("LocalPluginRegistry should handle corrupt index") {
    val oldRegistry = newLocalPluginRegistry
    val newPlugin = "org.jetbrains.plugins.hocon".toPlugin
    val newPluginRoot = pluginsFolder / "hocon"

    usingFakePlugin("hocon", hoconXML) {
      oldRegistry.markPluginInstalled(newPlugin, newPluginRoot)
    }
    NioUtils.delete(newPluginRoot) // so that registry won't reindex

    Using.resource(new OutputStreamWriter(Files.newOutputStream(pluginsIndex))) { writer =>
      writer.write(0xff)
    }

    val newRegistry = newLocalPluginRegistry

    val messages: Seq[String] = captureLog {
      newRegistry.isPluginInstalled(newPlugin) shouldBe false // deleted plugin is not re-indexed
      forAll(bundledPlugins) {
        newRegistry.isPluginInstalled(_) shouldBe true
      }
    }


    def assertContainsMessageStartingWith(messages: Seq[String], prefix: String): Unit = {
      assert(
        messages.exists(_.startsWith(prefix)),
        s"""No message starting with: '$prefix'
           |Available messages:
           |${messages.mkString("\n")}""".stripMargin
      )
    }

    assertContainsMessageStartingWith(messages, "[warn] Failed to load plugin index from disk: ")
  }

  test("irrelevant files and folders are ignored when building index") {
    Files.createDirectory(pluginsFolder / "NON-PLUGIN")
    Files.createFile(pluginsFolder / ".DS_Store")

    val registry = newLocalPluginRegistry

    val messages = captureLog {
      registry.isPluginInstalled(bundledPlugins.head) shouldBe true
    }

    def assertHasMatchingMessage(regex: String): Unit = {
      if (!messages.exists(_.matches(regex))) {
        fail(
          s"""No matching message for regex: $regex
             |Available messages:
             |${messages.mkString("\n")}""".stripMargin
        )
      }
    }

    //NOTE: non ".jar" and non-directories are silently ignored
    //assertHasMatchingMessage("""\[warn\] Failed to add plugin to index: Couldn't find plugin.xml in .+\.DS_Store""")
    assertHasMatchingMessage("""\[warn\] Failed to add plugin to index: Plugin root .+NON-PLUGIN has no lib directory""")
  }

  private val PluginIndexXmlFileContent: String =
    """<pluginIndex>
      |  <version>1</version>
      |  <plugins>
      |    <plugin>
      |      <id>com.jetbrains.codeWithMe</id>
      |      <path>plugins/cwm-plugin</path>
      |      <descriptor>
      |        <idea-plugin>
      |          <name>Code With Me</name>
      |          <vendor>JetBrains</vendor>
      |          <id>com.jetbrains.codeWithMe</id>
      |          <version>232.3318</version>
      |          <idea-version since-build="232.3318" until-build="232.3318"/>
      |          <depends optional="false">com.intellij.modules.platform</depends>
      |          <depends optional="false">com.jetbrains.projector.libs</depends>
      |          <depends optional="true">org.jetbrains.plugins.terminal</depends>
      |          <depends optional="true">com.intellij.java</depends>
      |          <depends optional="true">Pythonid</depends>
      |        </idea-plugin>
      |      </descriptor>
      |    </plugin>
      |    <plugin>
      |      <id>org.jetbrains.plugins.yaml</id>
      |      <path>plugins/yaml.jar</path>
      |      <descriptor>
      |        <idea-plugin>
      |          <name>YAML</name>
      |          <vendor>JetBrains</vendor>
      |          <id>org.jetbrains.plugins.yaml</id>
      |          <version>211.5538.2</version>
      |          <idea-version since-build="211.5538.2" until-build="211.5538.2"/>
      |          <depends optional="false">com.intellij.modules.lang</depends>
      |        </idea-plugin>
      |      </descriptor>
      |    </plugin>
      |    <plugin>
      |      <id>com.intellij.properties</id>
      |      <path>plugins/properties</path>
      |      <descriptor>
      |        <idea-plugin>
      |          <name>Properties</name>
      |          <vendor>JetBrains</vendor>
      |          <id>com.intellij.properties</id>
      |          <version>242.14146.5</version>
      |          <idea-version since-build="242.14146.5" until-build="242.14146.5"/>
      |        </idea-plugin>
      |      </descriptor>
      |    </plugin>
      |  </plugins>
      |</pluginIndex>""".stripMargin.trim

  test("check plugin index file content") {
    val pluginIndex = new PluginIndexImpl(ideaRoot)

    // This triggers index initialization and creation of plugin index file
    pluginIndex.getAllDescriptors

    pluginsIndex.toFile should exist

    val content = FileUtils.readFileToString(pluginsIndex.toFile, StandardCharsets.UTF_8)
    content shouldBe PluginIndexXmlFileContent
  }

  test("check plugin index file deserialisation") {
    Files.write(pluginsIndex, PluginIndexXmlFileContent.getBytes(StandardCharsets.UTF_8))

    val pluginIndex = new PluginIndexImpl(ideaRoot)

    val descriptors = pluginIndex.getAllDescriptors

    descriptors.size shouldBe 3

    // Verify the plugin descriptors
    val cwmPlugin = descriptors.find(_.id == "com.jetbrains.codeWithMe").get
    cwmPlugin.id shouldBe "com.jetbrains.codeWithMe"
    cwmPlugin.vendor shouldBe "JetBrains"
    cwmPlugin.name shouldBe "Code With Me"
    cwmPlugin.version shouldBe "232.3318"
    cwmPlugin.sinceBuild shouldBe "232.3318"
    cwmPlugin.untilBuild shouldBe "232.3318"
    cwmPlugin.dependsOn should contain(PluginDescriptor.Dependency("com.intellij.modules.platform", optional = false))
    cwmPlugin.dependsOn should contain(PluginDescriptor.Dependency("com.jetbrains.projector.libs", optional = false))
    cwmPlugin.dependsOn should contain(PluginDescriptor.Dependency("org.jetbrains.plugins.terminal", optional = true))
    cwmPlugin.dependsOn should contain(PluginDescriptor.Dependency("com.intellij.java", optional = true))
    cwmPlugin.dependsOn should contain(PluginDescriptor.Dependency("Pythonid", optional = true))

    val yamlPlugin = descriptors.find(_.id == "org.jetbrains.plugins.yaml").get
    yamlPlugin.id shouldBe "org.jetbrains.plugins.yaml"
    yamlPlugin.vendor shouldBe "JetBrains"
    yamlPlugin.name shouldBe "YAML"
    yamlPlugin.version shouldBe "211.5538.2"
    yamlPlugin.sinceBuild shouldBe "211.5538.2"
    yamlPlugin.untilBuild shouldBe "211.5538.2"
    yamlPlugin.dependsOn should contain(PluginDescriptor.Dependency("com.intellij.modules.lang", optional = false))

    val propertiesPlugin = descriptors.find(_.id == "com.intellij.properties").get
    propertiesPlugin.id shouldBe "com.intellij.properties"
    propertiesPlugin.vendor shouldBe "JetBrains"
    propertiesPlugin.name shouldBe "Properties"
    propertiesPlugin.version shouldBe "242.14146.5"
    propertiesPlugin.sinceBuild shouldBe "242.14146.5"
    propertiesPlugin.untilBuild shouldBe "242.14146.5"
    propertiesPlugin.dependsOn shouldBe empty
  }
}
