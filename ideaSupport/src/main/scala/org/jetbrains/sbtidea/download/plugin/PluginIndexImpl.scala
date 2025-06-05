package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.annotations.TestOnly
import org.jetbrains.sbtidea.download.plugin.serialization.{PluginIndexSerializer, XmlPluginIndexSerializer}
import org.jetbrains.sbtidea.{PathExt, PluginLogger as log}
import sbt.*

import java.nio.file.{Files, Path}
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.asScalaBufferConverter
import scala.util.Using

class PluginIndexImpl(ideaRoot: Path) extends PluginIndex {

  import PluginIndexImpl.*

  private type PluginId = String
  private type ReprMutable = scala.collection.mutable.HashMap[PluginId, PluginInfo]
  private type Repr = scala.collection.Map[PluginId, PluginInfo]

  private val indexFile: Path = ideaRoot / PluginsIndexFilename

  private lazy val index: ReprMutable = {
    val result = initIndex

    val mutable = new ReprMutable
    mutable ++= result
    mutable
  }

  private def initIndex: Repr = {
    if (indexFile.exists) {
      try {
        loadFromFile(indexFile)
      } catch {
        case e: Throwable =>
          log.warn(s"Failed to load plugin index from disk: $e")
          indexFile.toFile.delete()
          buildAndSaveIndex()
      }
    } else {
      buildAndSaveIndex()
    }
  }

  private def buildAndSaveIndex(): Repr = {
    val plugins = buildFromPluginsDir
    try {
      val pluginIds = plugins.keys.toSeq
        .filter(_.trim.nonEmpty) // for some reason, there is some empty id
        .sorted
        .mkString(", ")
      log.info(s"Plugin ids from $PluginsIndexFilename: $pluginIds")

      saveToFile(plugins)
    } catch {
      case e: Throwable =>
        log.warn(s"Failed to write back plugin index: $e")
    }
    plugins
  }

  override def put(descriptor: PluginDescriptor, installPath: Path, downloadedPluginFileName: Option[String]): Unit = {
    index += descriptor.id -> PluginInfo(installPath, descriptor, downloadedPluginFileName)
    saveToFile(index)
  }

  override def contains(id: String): Boolean =
    index.contains(id)

  override def getInstallRoot(id: String): Option[Path] =
    index.get(id).map(_.installPath)

  override def getPluginDescriptor(id: String): Option[PluginDescriptor] =
    index.get(id).map(_.descriptor)

  override def getDownloadedPluginFileName(id: String): Option[String] =
    index.get(id).flatMap(_.downloadedFileName)

  override def getAllDescriptors: Seq[PluginDescriptor] = index.values.map(_.descriptor).toSeq

  private def loadFromFile(file: Path): ReprMutable = {
    val buffer = new ReprMutable
    val data = IndexSerializer.load(file)
    val dataWithAbsolutePaths = data.map { case (id, info) => (id, info.withAbsoluteInstallPath(ideaRoot))}
    buffer ++= dataWithAbsolutePaths
    buffer
  }

  private def saveToFile(idx: Repr): Unit = {
    val dataWithRelativePaths = idx.mapValues(_.withRelativeInstallPath(ideaRoot)).toSeq.sortBy(_._1)
    IndexSerializer.save(indexFile, dataWithRelativePaths)
  }

  private def buildFromPluginsDir: Map[PluginId, PluginInfo] = {
    val allFilesInPluginsDir = Using.resource(Files.list(ideaRoot.resolve("plugins")))(_.collect(Collectors.toList[Path]).asScala)
    val pluginDirsOfJarFiles = allFilesInPluginsDir.filter { file =>
      //extra filtering of unexpected extensions (e.g., some strange file plugin-classpath.txt)
      file.isDir || file.toString.endsWith(".jar")
    }
    pluginDirsOfJarFiles.flatMap { pluginDir =>
      val pluginMetaData = LocalPluginRegistry.extractPluginMetaData(pluginDir)
      pluginMetaData match {
        case Left(error) =>
          log.warn(s"Failed to add plugin to index: $error")
          None
        case Right(descriptor) =>
          Some(descriptor.id -> PluginInfo(pluginDir, descriptor, None))
      }
    }.toMap
  }
}

object PluginIndexImpl {
  private val IndexSerializer: PluginIndexSerializer = XmlPluginIndexSerializer

  @TestOnly val PluginsIndexFilename = "plugins_index.xml"

  /**
   * Represents information about a plugin in the plugin index.
   *
   * @param installPath        the path where the plugin is installed
   * @param descriptor         the plugin descriptor
   * @param downloadedFileName the name of the downloaded plugin file, if the plugin was downloaded
   */
  case class PluginInfo(
    installPath: Path,
    descriptor: PluginDescriptor,
    downloadedFileName: Option[String]
  ) {
    def withAbsoluteInstallPath(ideaRoot: Path): PluginInfo =
      copy(installPath = ideaRoot.resolve(installPath))

    def withRelativeInstallPath(ideaRoot: Path): PluginInfo =
      copy(installPath = ideaRoot.relativize(installPath))
  }
}
