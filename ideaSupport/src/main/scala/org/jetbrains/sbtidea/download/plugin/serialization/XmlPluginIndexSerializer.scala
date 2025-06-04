package org.jetbrains.sbtidea.download.plugin.serialization

import org.jetbrains.sbtidea.download.plugin.PluginDescriptor
import org.jetbrains.sbtidea.download.plugin.PluginIndexImpl.PluginInfo

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.{Map, mutable}
import scala.xml.*

object XmlPluginIndexSerializer extends PluginIndexSerializer {

  override def load(file: Path): Map[String, PluginInfo] = {
    val buffer = new mutable.HashMap[String, PluginInfo]

    val content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
    val xml = XML.loadString(content)

    val version = (xml \ "version").text.toInt
    if (version != IndexVersion) {
      throw new WrongIndexVersionException(version)
    }

    val plugins = xml \ "plugins" \ "plugin"
    plugins.foreach { plugin =>
      val id = (plugin \ "id").text
      val relativePath = (plugin \ "path").text
      val downloadedFileName = (plugin \ "downloadedFileName").headOption.map(_.text)

      // Get the descriptor XML node and convert it to a PluginDescriptor
      val descriptorNode = (plugin \ "descriptor").head
      val descriptor = PluginDescriptor.load(descriptorNode.asInstanceOf[Elem])

      buffer.put(id, PluginInfo(Path.of(relativePath), descriptor, downloadedFileName))
    }

    buffer
  }

  override def save(file: Path, data: Map[String, PluginInfo]): Unit = {
    val plugins = data.map { case (id, info) =>
      val descriptorXml = XML.loadString(info.descriptor.toXMLStr)

      <plugin>
        <id>{id}</id>
        <path>{info.installPath}</path>
        <descriptor>{descriptorXml}</descriptor>
        {if (info.downloadedFileName.isDefined) <downloadedFileName>{info.downloadedFileName.get}</downloadedFileName>}
      </plugin>
    }.toSeq

    val indexXml =
      <pluginIndex>
        <version>{IndexVersion}</version>
        <plugins>{plugins}</plugins>
      </pluginIndex>

    val printer = new PrettyPrinter(120, 2)
    val formattedXml = printer.format(indexXml)

    Files.write(file, formattedXml.getBytes(StandardCharsets.UTF_8))
  }

  private val IndexVersion = 1

  private class WrongIndexVersionException(fileIndex: Int)
    extends RuntimeException(s"Index version in file $fileIndex is different from current $IndexVersion")
}
