package org.jetbrains.sbtidea.download.plugin

import java.nio.file.{Files, Path}
import java.util

import org.jetbrains.sbtidea.Keys.IntellijPlugin
import org.jetbrains.sbtidea.download._
import org.jetbrains.sbtidea.{pathToPathExt, PluginLogger => log}
import sbt._

import scala.collection.mutable

class LocalPluginRegistry (ideaRoot: Path) extends LocalPluginRegistryApi {
  import LocalPluginRegistry._

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
      }
      if (!index.contains(key))
        throw new MissingPluginRootException(ideaPlugin.toString)
      val path = index.getInstallRoot(key)
      path.get
  }
}


object LocalPluginRegistry {
  import org.jetbrains.sbtidea._

  implicit class HashMapExt[K, V](val hm: util.HashMap[K,V]) extends AnyVal {
    def getOrError[T](err: => T)(key: K): Either[T, V] = hm
      .get(key)
      .lift2Option
      .map(Right(_))
      .getOrElse(Left(err))
  }

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
