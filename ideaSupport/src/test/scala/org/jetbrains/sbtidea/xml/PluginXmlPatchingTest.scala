package org.jetbrains.sbtidea.xml

import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.jetbrains.sbtidea.pluginXmlOptions
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, StandardCopyOption}
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.util.Using

class PluginXmlPatchingTest extends AnyFunSuite with Matchers with IdeaMock {

  private def getPluginXml(pluginXmlName: String = "plugin.xml") = {
    val tmpFile = createTempFile("", "plugin.xml")
    Using.resource(getClass.getResourceAsStream(pluginXmlName)) { stream =>
      Files.copy(stream, tmpFile, StandardCopyOption.REPLACE_EXISTING)
    }
    tmpFile
  }

  test("patch all fields test") {
    val options = pluginXmlOptions { xml =>
      xml.version           = "VERSION"
      xml.changeNotes       = "CHANGE_NOTES"
      xml.pluginDescription = "DESCRIPTION"
      xml.sinceBuild        = "SINCE_BUILD"
      xml.untilBuild        = "UNTIL_BUILD"
    }
    val pluginXml = getPluginXml()
    val initialContent = Files.readAllLines(pluginXml).asScala
    val patcher = new PluginXmlPatcher(pluginXml)
    val result = patcher.patch(options)
    val newContent = Files.readAllLines(result).asScala

    val diff = newContent.toSet &~ initialContent.toSet

    diff.size shouldBe 4
    diff should contain (s"""    <change-notes>${options.changeNotes}</change-notes>""")
    diff should contain (s"""    <version>${options.version}</version>""")
    diff should contain (s"""    <description>${options.pluginDescription}</description>""")
    diff should contain (s"""    <idea-version since-build="${options.sinceBuild}" until-build="${options.untilBuild}"/>""")
  }

  test("patch only since fields test") {
    val options = pluginXmlOptions { xml =>
      xml.version           = "VERSION"
      xml.changeNotes       = "CHANGE_NOTES"
      xml.pluginDescription = "DESCRIPTION"
      xml.sinceBuild        = "SINCE_BUILD"
    }
    val pluginXml = getPluginXml()
    val initialContent = Files.readAllLines(pluginXml).asScala
    val patcher = new PluginXmlPatcher(pluginXml)
    val result = patcher.patch(options)
    val newContent = Files.readAllLines(result).asScala

    val diff = newContent.toSet &~ initialContent.toSet

    diff.size shouldBe 4
    diff should contain (s"""    <change-notes>${options.changeNotes}</change-notes>""")
    diff should contain (s"""    <version>${options.version}</version>""")
    diff should contain (s"""    <description>${options.pluginDescription}</description>""")
    diff should contain (s"""    <idea-version since-build="${options.sinceBuild}"/>""")
  }

  test("patch only until fields test") {
    val options = pluginXmlOptions { xml =>
      xml.version           = "VERSION"
      xml.changeNotes       = "CHANGE_NOTES"
      xml.pluginDescription = "DESCRIPTION"
      xml.untilBuild        = "UNTIL_BUILD"
    }
    val pluginXml = getPluginXml()
    val initialContent = Files.readAllLines(pluginXml).asScala
    val patcher = new PluginXmlPatcher(pluginXml)
    val result = patcher.patch(options)
    val newContent = Files.readAllLines(result).asScala

    val diff = newContent.toSet &~ initialContent.toSet

    diff.size shouldBe 4
    diff should contain (s"""    <change-notes>${options.changeNotes}</change-notes>""")
    diff should contain (s"""    <version>${options.version}</version>""")
    diff should contain (s"""    <description>${options.pluginDescription}</description>""")
    diff should contain (s"""    <idea-version until-build="${options.untilBuild}"/>""")
  }

  test("no modifications done if no patching options are defined") {
    val options = pluginXmlOptions { _ => }
    val pluginXml = getPluginXml()
    val initialContent = Files.readAllLines(pluginXml).asScala
    val patcher = new PluginXmlPatcher(pluginXml)
    val result = patcher.patch(options)
    val newContent = Files.readAllLines(result).asScala

    val diff = newContent.toSet &~ initialContent.toSet

    diff.size shouldBe 0
  }

  test("multi-line tags are patched correctly") {
    val pluginXml = getPluginXml("plugin-multilinetag.xml")

    val options = pluginXmlOptions { xml =>
      xml.pluginDescription = "DESCRIPTION\nLINE2"
    }
    val patcher = new PluginXmlPatcher(pluginXml)
    val result = patcher.patch(options)
    val newContent = Files.readAllLines(result).asScala

    val newContentPattern = s"(?s).*<description>${options.pluginDescription}</description>.*$$".r
    newContent.mkString("\n") should fullyMatch regex newContentPattern
  }

  test("duplicate tags are only patched the first one") {
    val pluginXml = getPluginXml("plugin-duplicatedtags.xml")

    val options = pluginXmlOptions { xml =>
      xml.pluginDescription = "DESCRIPTION\nLINE2"
    }
    val patcher = new PluginXmlPatcher(pluginXml)
    val result = patcher.patch(options)
    val newContent = Files.readAllLines(result).asScala

    val pattern1 = s"(?s)<description>.*?</description>".r
    pattern1.findAllMatchIn(newContent.mkString("\n")).size shouldBe 2
    val pattern2 = s"(?s).*<description>${options.pluginDescription}</description>.*<description>.*</description>.*$$".r
    newContent.mkString("\n") should fullyMatch regex pattern2
  }
}
