package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.download.*
import org.jetbrains.sbtidea.download.api.InstallContext
import org.jetbrains.sbtidea.{IntellijPlugin, PathExt, PluginLogger as log}
import sbt.*

import java.nio.file.{Files, Path}

class LocalPluginRegistry(ctx: InstallContext) extends LocalPluginRegistryApi {
  import LocalPluginRegistry.*

  private val ideaRoot = ctx.baseDirectory

  lazy val index = new PluginIndexImpl(ideaRoot)

  private def getDescriptorFromPluginFolder(name: String): Either[String, PluginDescriptor] =
    extractPluginMetaData(ideaRoot / "plugins" / name)

  override def getPluginDescriptor(ideaPlugin: IntellijPlugin): Either[String, PluginDescriptor] = ideaPlugin match {
    case idOwner: IntellijPlugin.WithKnownId =>
      index.getPluginDescriptor(idOwner.id).toRight(s"Can't find plugin descriptor with id ${idOwner.id} in index ")
    case IntellijPlugin.BundledFolder(name) =>
      getDescriptorFromPluginFolder(name)
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
    val existsInIndex = ideaPlugin match {
      case IntellijPlugin.Id(id,  _, _, _) =>
        index.contains(id)
      case IntellijPlugin.IdWithDownloadUrl(id, _) =>
        index.contains(id)
      case IntellijPlugin.BundledFolder(name) => getDescriptorFromPluginFolder(name) match {
        case Right(descriptor) =>
          if (!index.contains(descriptor.id)) {
            log.warn(s"Bundled plugin folder - '$name(${descriptor.id})' exists but not in index: corrupt index file?")
          }
          true
        case Left(_)  =>
          false
      }
    }
    if (existsInIndex) {
      val path = getInstalledPluginRoot(ideaPlugin)
      if (!path.exists) {
        log.warn(s"Plugin was registered in index but plugin installation directory does not exist: $path")
      }
      path.exists
    }
    else false
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
        case id: IntellijPlugin.Id => id.id
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

  private class MissingPluginRootException(pluginName: String)
    extends RuntimeException(s"Can't find plugin root for $pluginName: check plugin name")

  def extractInstalledPluginDescriptorFileContent(pluginRoot: Path): Either[String, PluginXmlContent] =
    extractInstalledPluginDescriptorFileContent(pluginRoot, "plugin.xml")

  private def extractInstalledPluginDescriptorFileContent(pluginRoot: Path, pluginXmlFileName: String): Either[String, PluginXmlContent] = {
    def couldNotFindPluginXml = s"Couldn't find $pluginXmlFileName in $pluginRoot"

    try {
      val pluginXmlDetector = new PluginXmlDetector(pluginXmlFileName)

      if (Files.isDirectory(pluginRoot)) {
        val lib = pluginRoot.resolve("lib")
        if (lib.toFile.exists())
          pluginXmlDetector.findPluginXmlContentInDir(lib).toRight(couldNotFindPluginXml)
        else
          Left(s"Plugin root $pluginRoot has no lib directory")
      } else
        pluginXmlDetector.pluginXmlContent(pluginRoot).toRight(couldNotFindPluginXml)
    }
    catch {
      case e: Exception =>
        Left(s"Error during detecting plugin.xml: $e")
    }
  }

  def extractPluginMetaData(pluginRoot: Path): Either[String, PluginDescriptor] = {
    val content1 = extractInstalledPluginDescriptorFileContent(pluginRoot, PluginXmlDetector.PluginXmlFileNames.Default)
    val content2 = extractInstalledPluginDescriptorFileContent(pluginRoot, PluginXmlDetector.PluginXmlFileNames.PluginBaseXml)

    val descriptor1 = content1.map(_.content).map(PluginDescriptor.load)
    val descriptor2 = content2.map(_.content).map(PluginDescriptor.load)

    val descriptorFinal = descriptor1.map { d1 =>
      descriptor2 match {
        case Right(d2) =>
          d1.merge(d2)
        case Left(_) =>
          d1
      }
    }
    descriptorFinal
  }
}
