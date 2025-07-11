package org.jetbrains.sbtidea.download.plugin.serialization

import org.jetbrains.sbtidea.download.plugin.{PluginDescriptor, PluginInfo}
import org.jetbrains.sbtidea.download.plugin.PluginInfo.PluginDownloadInfo

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.net.URL
import java.time.LocalDateTime
import scala.collection.mutable
import scala.xml.*

object XmlPluginIndexSerializer extends PluginIndexSerializer {

  override def load(file: Path): Seq[(String, PluginInfo)] = {
    val buffer = new mutable.ArrayBuffer[(String, PluginInfo)]

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

      // Parse downloadInfo if present
      val downloadInfo = (plugin \ "downloadInfo").headOption.map { infoNode =>
        val fileName = (infoNode \ "downloadedFileName").text
        val url = new java.net.URL((infoNode \ "downloadedUrl").text)
        val timeStr = (infoNode \ "downloadedTime").text
        val time = java.time.LocalDateTime.parse(timeStr)

        PluginInfo.PluginDownloadInfo(fileName, url, time)
      }

      // Get the descriptor XML node and convert it to a PluginDescriptor
      val descriptorNode = (plugin \ "descriptor").head
      val descriptor = PluginDescriptor.load(descriptorNode.asInstanceOf[Elem])

      buffer += id -> PluginInfo(Path.of(relativePath), descriptor, downloadInfo)
    }

    buffer
  }

  override def save(file: Path, data: Seq[(String, PluginInfo)]): Unit = {
    val plugins = data.map { case (id, info) =>
      val descriptorXml = XML.loadString(info.descriptor.toXMLStr)

      val downloadInfoXml = info.downloadInfo.map { downloadInfo =>
        <downloadInfo>
          <downloadedFileName>{downloadInfo.downloadedFileName}</downloadedFileName>
          <downloadedUrl>{downloadInfo.downloadedUrl.toString}</downloadedUrl>
          <downloadedTime>{downloadInfo.downloadedTime.toString}</downloadedTime>
        </downloadInfo>
      }

      <plugin>
        <id>{id}</id>
        <path>{info.installPath}</path>
        <descriptor>{descriptorXml}</descriptor>
        {if (downloadInfoXml.isDefined) downloadInfoXml.get}
      </plugin>
    }

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
