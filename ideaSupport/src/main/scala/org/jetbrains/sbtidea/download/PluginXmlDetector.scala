package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.IteratorExt

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}
import java.util.Collections
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.asScalaBufferConverter
import scala.util.Using

private class PluginXmlDetector(pluginXmlFileName: String = PluginXmlDetector.PluginXmlFileNames.Default) {

  private val EmptyEnvVars = Collections.emptyMap[String, Any]()

  def isPluginJar(path: Path): Boolean =
    pluginXmlContent(path).isDefined

  def findPluginXmlContentInDir(dir: Path): Option[PluginXmlContent] = {
    val files = Files.list(dir).collect(Collectors.toList[Path]).asScala
    files.iterator.flatMap(pluginXmlContent).headOption
  }

  def pluginXmlContent(path: Path): Option[PluginXmlContent] = {
    val isJarFile = path.toString.endsWith(".jar")
    if (!isJarFile)
      return None

    val uri = URI.create(s"jar:${path.toUri}")

    try
      Using.resource(FileSystems.newFileSystem(uri, EmptyEnvVars)) { fs =>
        val maybePluginXmlPath = Some(fs.getPath("META-INF", pluginXmlFileName)).filter(Files.exists(_))
        maybePluginXmlPath.map { path =>
          val content = new String(Files.readAllBytes(path))
          PluginXmlContent(path, content)
        }
      }
    catch {
      case e: java.util.zip.ZipError =>
        throw new RuntimeException(s"Corrupt zip file: $path", e)
    }
  }
}

private object PluginXmlDetector {
  val Default = new PluginXmlDetector

  /** See comment for [[org.jetbrains.sbtidea.download.plugin.PluginDescriptor#merge]] */
  object PluginXmlFileNames {
    val Default = "plugin.xml"
    val PluginBaseXml = "pluginBase.xml"
  }
}

