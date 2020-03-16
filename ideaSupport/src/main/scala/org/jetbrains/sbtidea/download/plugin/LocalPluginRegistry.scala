package org.jetbrains.sbtidea.download.plugin

import java.io._
import java.nio.file.{Files, Path, Paths}
import java.util
import java.util.function.Consumer

import org.jetbrains.sbtidea.Keys.IntellijPlugin
import org.jetbrains.sbtidea.{PluginLogger => log}
import org.jetbrains.sbtidea.packaging.artifact.using
import org.jetbrains.sbtidea.download._
import org.jetbrains.sbtidea.pathToPathExt
import sbt._

import scala.collection.mutable

class LocalPluginRegistry (ideaRoot: Path) extends LocalPluginRegistryApi {
  import LocalPluginRegistry._

  type PluginIndex = util.HashMap[String, String]
  private def emptyIndex = new PluginIndex

  private val indexFile = ideaRoot.resolve("plugins.idx")

  private lazy val index: PluginIndex = buildIdeaPluginIndex()

  private def buildIdeaPluginIndex(): PluginIndex = {
    val fromPluginsDir = emptyIndex
    class IndexBuilder extends Consumer[Path] {
      override def accept(path: Path): Unit = extractPluginMetaData(path) match {
        case Left(error)      => log.warn(s"Failed to add plugin to index: $error")
        case Right(metadata)  => fromPluginsDir.put(metadata.id.repr, path.toString)
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
    else try {
      using(new ObjectInputStream(new BufferedInputStream(new FileInputStream(indexFile.toFile)))) { stream =>
        stream.readObject() match {
          case m: PluginIndex => m
          case other =>
            log.warn(s"Unexpected data type in plugin index: ${other.getClass}")
            Files.delete(indexFile)
            emptyIndex
        }
      }
    } catch {
      case e: Exception =>
        log.warn(s"Failed to load local plugin index: $e")
        Files.delete(indexFile)
        emptyIndex
    }
  }

  private def writeIndexFile(): Unit =
    using(new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile.toFile)))) { stream =>
      stream.writeObject(index)
    }

  private def getDescriptorFromPluginFolder(name: String): Either[String, PluginDescriptor] =
    extractPluginMetaData(ideaRoot / "plugins" / name)

  override def getPluginDescriptor(ideaPlugin: IntellijPlugin): Either[String, PluginDescriptor] =
    extractPluginMetaData(getInstalledPluginRoot(ideaPlugin))


  override def markPluginInstalled(ideaPlugin: IntellijPlugin, to: Path): Unit = {
    val key = ideaPlugin match {
      case IntellijPlugin.Url(url) => url.toString
      case IntellijPlugin.Id(id, _, _) => id
    }
    index.put(key, to.toString)
    writeIndexFile()
  }

  override def isPluginInstalled(ideaPlugin: IntellijPlugin): Boolean = {
    ideaPlugin match {
      case IntellijPlugin.Url(url) => index.containsKey(url.toString)
      case IntellijPlugin.Id(id,  _, _) => index.containsKey(id)
      case IntellijPlugin.BundledFolder(name) => getDescriptorFromPluginFolder(name) match {
        case Right(descriptor) if index.containsKey(descriptor.id) =>
          true
        case Right(descriptor) =>
          log.warn(s"Bundled plugin folder - '$name(${descriptor.id})' exists but not in index: corrupt index file?")
          true
        case Left(_)  =>
          false
      }
    }
  }

  override def getInstalledPluginRoot(ideaPlugin: IntellijPlugin): Path = ideaPlugin match {
    case IntellijPlugin.BundledFolder(name) =>
      val pluginRoot = ideaRoot / "plugins" / name
      if (pluginRoot.exists)
        pluginRoot
      else
        throw new MissingPluginRootException(ideaPlugin.toString)
    case _ =>
      val key = ideaPlugin match {
        case IntellijPlugin.Url(url) => url.toString
        case IntellijPlugin.Id(id, _, _) => id
      }
      if (!index.containsKey(key))
        throw new MissingPluginRootException(ideaPlugin.toString)
      Paths.get(index.get(key))
  }
}


object LocalPluginRegistry {

  private val instances: mutable.Map[Path, LocalPluginRegistry] =
    new mutable.WeakHashMap[Path, LocalPluginRegistry]().withDefault(new LocalPluginRegistry(_))

  class MissingPluginRootException(pluginName: String) extends
    RuntimeException(s"Can't find plugin root for $pluginName: check plugin name")

  def extractInstalledPluginDescriptor(pluginRoot: Path): Either[String, String] = {
    try {
      if (Files.isDirectory(pluginRoot)) {
        val lib = pluginRoot.resolve("lib")
        if (!lib.toFile.exists())
          return Left(s"Plugin root $pluginRoot has no lib directory")
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
    } catch {
      case e: Exception => return Left(s"Error during detecting plugin.xml: $e")
    }
    Left(s"Couldn't find plugin.xml in $pluginRoot")
  }

  def extractPluginMetaData(pluginRoot: Path): Either[String, PluginDescriptor] =
    extractInstalledPluginDescriptor(pluginRoot)
      .fold(
        err => Left(err),
        data => Right(PluginDescriptor.load(data))
      )

  def instanceFor(ideaRoot: Path): LocalPluginRegistry = instances(ideaRoot)
}
