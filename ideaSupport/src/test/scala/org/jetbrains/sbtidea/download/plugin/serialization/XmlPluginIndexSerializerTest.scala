package org.jetbrains.sbtidea.download.plugin.serialization

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.io.File
import java.nio.file.{Files, Path}

class XmlPluginIndexSerializerTest extends AnyFunSuiteLike {

  private val serializer = XmlPluginIndexSerializer

  private val xmlFile = Path.of(this.getClass.getResource("test_plugin_index_content").getPath)

  test("load and save do not crash") {
    val newXmlFile = File.createTempFile("out", "xml").toPath
    try {
      val data = serializer.load(xmlFile)
      serializer.save(newXmlFile, data)

      val data2 = serializer.load(newXmlFile)

      data shouldBe data2
    } finally {
      Files.delete(newXmlFile)
    }
  }
}
