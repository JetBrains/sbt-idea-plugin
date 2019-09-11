package org.jetbrains.sbtidea.download

import java.io._
import java.nio.file.{Files, Path, Paths}
import java.util
import java.util.function.Consumer

import org.jetbrains.sbtidea.Keys.IdeaPlugin
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.api.PluginMetadata
import org.jetbrains.sbtidea.packaging.artifact.using

import scala.xml.XML

class LocalPluginRegistry(ideaRoot: Path, log: PluginLogger) {
  import LocalPluginRegistry._

  type PluginIndex = util.HashMap[String, String]
  private def emptyIndex = new PluginIndex

  private val indexFile = ideaRoot.resolve("plugins.idx")

  private lazy val index: PluginIndex = buildIdeaPluginIndex()

  private def buildIdeaPluginIndex(): PluginIndex = {
    val fromPluginsDir = emptyIndex
    class IndexBuilder extends Consumer[Path] {
      override def accept(path: Path): Unit = extractPluginMetaData(path) match {
        case Left(error) => log.error(s"Failed to build plugin index from $path: $error")
        case Right(metadata) => fromPluginsDir.put(metadata.id, path.toString)
      }
    }
    Files.list(ideaRoot.resolve("plugins")).forEach(new IndexBuilder)
    val fromIndexFile = readIndexFile()
    fromPluginsDir.putAll(fromIndexFile)
    fromPluginsDir
  }

  private def readIndexFile(): PluginIndex = {
    if (!indexFile.toFile.exists())
      emptyIndex
    else
      using(new ObjectInputStream(new BufferedInputStream(new FileInputStream(indexFile.toFile)))) { stream =>
        try {
          stream.readObject() match {
            case m: PluginIndex => m
            case other =>
              log.warn(s"Unexpected data type in plugin index: ${other.getClass}")
              Files.delete(indexFile)
              emptyIndex
          }
        } catch {
          case e: Exception =>
            log.warn(s"Failed to load local plugin index: ${e.getMessage}")
            Files.delete(indexFile)
            emptyIndex
        }
      }
  }

  private def writeIndexFile(): Unit =
    using(new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile.toFile)))) { stream =>
      stream.writeObject(index)
    }


  def markPluginInstalled(ideaPlugin: IdeaPlugin, to: Path): Unit = {
    val key = ideaPlugin match {
      case IdeaPlugin.Url(url) => url.toString
      case IdeaPlugin.Id(id, _, _) => id
    }
    index.put(key, to.toString)
    writeIndexFile()
  }

  def isPluginInstalled(ideaPlugin: IdeaPlugin): Boolean = {
    ideaPlugin match {
      case IdeaPlugin.Url(url) => index.containsKey(url.toString)
      case IdeaPlugin.Id(id, _, _) => index.containsKey(id)
    }
  }

  def getInstalledPluginRoot(ideaPlugin: IdeaPlugin): Path = {
    val key = ideaPlugin match {
      case IdeaPlugin.Url(url) => url
      case IdeaPlugin.Id(id, _, _) => id
    }
    if (!index.containsKey(key))
      throw new RuntimeException(s"Plugin $ideaPlugin not found in index")
    Paths.get(index.get(key))
  }
}


object LocalPluginRegistry {

  def extractInstalledPluginDescriptor(pluginRoot: Path): Either[String, String] = {
    if (Files.isDirectory(pluginRoot)) {
      val lib = pluginRoot.resolve("lib")
      val detector = new PluginXmlDetector
      val result = Files
        .list(lib)
        .filter(detector)
        .findFirst()
      if (result.isPresent)
        return Right(detector.result)
    } else {
      val detector = new PluginXmlDetector
      if (detector.test(pluginRoot))
        return Right(detector.result)
    }
    Left(s"Couldn't find plugin.xml in $pluginRoot")
  }

  def extractPluginMetaData(data: String): PluginMetadata = {
    val pluginXml = XML.withSAXParser(createNonValidatingParser).loadString(data)
    val id      = (pluginXml \\ "id").text
    val version = (pluginXml \\ "version").text
    val name    = (pluginXml \\ "name").text
    val since   = (pluginXml \\ "idea-version").head.attributes("since-build").text
    val until   = (pluginXml \\ "idea-version").head.attributes("until-build").text
    PluginMetadata(id = id, name = name, version = version, sinceBuild = since, untilBuild = until)
  }

  def extractPluginMetaData(pluginRoot: Path): Either[String, PluginMetadata] =
    extractInstalledPluginDescriptor(pluginRoot)
      .fold(
        err => Left(err),
        data => Right(extractPluginMetaData(data))
      )

  private def createNonValidatingParser = {
    val factory = javax.xml.parsers.SAXParserFactory.newInstance()
    // disable DTD validation
    factory.setValidating(false)
    factory.setFeature("http://xml.org/sax/features/validation", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.newSAXParser()
  }
}