package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.download.*
import org.jetbrains.sbtidea.{IntellijPlugin, pathToPathExt, PluginLogger as log}
import sbt.*

import java.nio.file.{Files, Path}
import scala.collection.mutable

class LocalPluginRegistry (ideaRoot: Path) extends LocalPluginRegistryApi {
  import LocalPluginRegistry.*

  val index = new PluginIndexImpl(ideaRoot)

  private def getDescriptorFromPluginFolder(name: String): Either[String, PluginDescriptor] =
    extractPluginMetaData(ideaRoot / "plugins" / name)

  override def getPluginDescriptor(ideaPlugin: IntellijPlugin): Either[String, PluginDescriptor] = ideaPlugin match {
    case IntellijPlugin.Url(url) =>
      index.getPluginDescriptor(url.toString).toRight("sdfsf")
    case IntellijPlugin.Id(id, _, _) =>
      index.getPluginDescriptor(id).toRight("f45f")
    case IntellijPlugin.BundledFolder(name) => getDescriptorFromPluginFolder(name)
  }

  override def getAllDescriptors: Seq[PluginDescriptor] = index.getAllDescriptors

  override def markPluginInstalled(ideaPlugin: IntellijPlugin, to: Path): Unit = {
    val descriptor = extractPluginMetaData(to)
    descriptor match {
      case Right(value) =>
        index.put(value, to)
      case Left(error) =>
        log.error(s"Failed to mark plugin installed, can't get plugin descriptor: $error")
    }
  }

  override def isPluginInstalled(ideaPlugin: IntellijPlugin): Boolean = {
    ideaPlugin match {
      case IntellijPlugin.Url(url) => index.contains(url.toString)
      case IntellijPlugin.Id(id,  _, _) => index.contains(id)
      case IntellijPlugin.BundledFolder(name) => getDescriptorFromPluginFolder(name) match {
        case Right(descriptor) if index.contains(descriptor.id) =>
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
        case unsupported =>
          throw new RuntimeException(s"Unsupported plugin: $unsupported")
      }
      if (!index.contains(key))
        throw new MissingPluginRootException(ideaPlugin.toString)
      val path = index.getInstallRoot(key)
      path.get
  }
}


object LocalPluginRegistry {

  private val instances: mutable.Map[Path, LocalPluginRegistry] =
    new mutable.WeakHashMap[Path, LocalPluginRegistry]().withDefault(new LocalPluginRegistry(_))

  private class MissingPluginRootException(pluginName: String)
    extends RuntimeException(s"Can't find plugin root for $pluginName: check plugin name")

  def extractInstalledPluginDescriptor(pluginRoot: Path): Either[String, PluginXmlContent] = {
    def couldNotFindPluginXml = s"Couldn't find plugin.xml in $pluginRoot"

    try
      if (Files.isDirectory(pluginRoot)) {
        val lib = pluginRoot.resolve("lib")
        if (lib.toFile.exists())
          PluginXmlDetector.findPluginContent(lib).toRight(couldNotFindPluginXml)
        else
          Left(s"Plugin root $pluginRoot has no lib directory")
      } else
        PluginXmlDetector.pluginXmlContent(pluginRoot).toRight(couldNotFindPluginXml)
    catch {
      case e: Exception =>
        Left(s"Error during detecting plugin.xml: $e")
    }
  }

  def extractPluginMetaData(pluginRoot: Path): Either[String, PluginDescriptor] = {
    val descriptor = extractInstalledPluginDescriptor(pluginRoot)
    descriptor
      .fold(
        err => Left(err),
        data => Right(PluginDescriptor.load(data.content))
      )
  }

  def instanceFor(ideaRoot: Path): LocalPluginRegistry = instances(ideaRoot)
}
