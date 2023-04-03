package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.IteratorExt

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}
import java.util.Collections
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.asScalaBufferConverter

private object PluginXmlDetector {

  import org.jetbrains.sbtidea.packaging.artifact.*

  private val EmptyEnvVars = Collections.emptyMap[String, Any]()

  def isPluginJar(path: Path): Boolean =
    pluginXmlFile(path).isDefined

  def pluginXmlContent(path: Path): Option[PluginXmlContent] = {
    val maybePath = pluginXmlFile(path)
    maybePath.map(readPluginXmlContent)
  }

  def findPluginContent(dir: Path): Option[PluginXmlContent] = {
    val maybePath = findPluginXmlFileInDir(dir)
    maybePath.map(readPluginXmlContent)
  }

  private def readPluginXmlContent(path: Path): PluginXmlContent = {
    val bytes = Files.readAllBytes(path)
    val content = new String(bytes)
    PluginXmlContent(path, content)
  }

  private def findPluginXmlFileInDir(dir: Path): Option[Path] = {
    val files = Files.list(dir).collect(Collectors.toList[Path]).asScala
    files.iterator.flatMap(pluginXmlFile).headOption
  }

  private def pluginXmlFile(path: Path): Option[Path] = {
    val isJarFile = path.toString.endsWith(".jar")
    if (!isJarFile)
      return None

    val uri = URI.create(s"jar:${path.toUri}")

    try
      using(FileSystems.newFileSystem(uri, EmptyEnvVars)) { fs =>
        val maybePluginXmlFile = fs.getPath("META-INF", "plugin.xml")
        Some(maybePluginXmlFile).filter(Files.exists(_))
      }
    catch {
      case e: java.util.zip.ZipError =>
        throw new RuntimeException(s"Corrupt zip file: $path", e)
    }
  }
}

case class PluginXmlContent(path: Path, content: String)
