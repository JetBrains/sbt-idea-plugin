package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.annotations.TestOnly
import org.jetbrains.sbtidea.download.plugin.LocalPluginRegistry.extractPluginMetaData
import org.jetbrains.sbtidea.{PathExt, PluginLogger as log}
import sbt.*

import java.io.*
import java.nio.file.{Files, Path}
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.asScalaBufferConverter
import scala.util.Using

class PluginIndexImpl(ideaRoot: Path) extends PluginIndex {

  import PluginIndexImpl.*

  private case class PluginInfo(installPath: Path, descriptor: PluginDescriptor, downloadedFileName: Option[String])
  
  private type PluginId = String
  private type ReprMutable = scala.collection.mutable.HashMap[PluginId, PluginInfo]
  private type Repr = scala.collection.Map[PluginId, PluginInfo]

  private val indexFile: Path = ideaRoot / INDEX_FILENAME

  private lazy val index: ReprMutable = {
    val result = initIndex

    val mutable = new ReprMutable
    mutable ++= result
    mutable
  }

  private def initIndex: Repr = {
    if (indexFile.exists) {
      try {
        loadFromFile()
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
      log.info(s"Plugin ids from $INDEX_FILENAME: $pluginIds")

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

  private def loadFromFile(): ReprMutable = {
    import PluginDescriptor.*
    val buffer = new ReprMutable
    Using.resource(new FileInputStream(indexFile.toFile)) { fis =>
      Using.resource(new ObjectInputStream(new BufferedInputStream(fis))) { stream =>
        val version = stream.readInt()
        val size = stream.readInt()
        if (version != INDEX_VERSION)
          throw new WrongIndexVersionException(version)
        val paths = stream.readObject().asInstanceOf[Array[String]]
        val descriptors = stream.readObject().asInstanceOf[Array[String]]
        val fileNames = if (version >= FILE_NAMES_SUPPORT_VERSION) stream.readObject().asInstanceOf[Array[Option[String]]] else Array.fill(size)(None)
        assert(paths.length == descriptors.length && paths.length == size,
          s"Data size mismatch: ${paths.length} - ${descriptors.length} $size")
        val data = paths
          .zip(descriptors)
          .zipWithIndex
          .map { case ((path, str), idx) => (path, load(str), fileNames(idx)) }
          .map { case (path, descriptor, fileName)  => descriptor.id -> PluginInfo(ideaRoot / path, descriptor, fileName) }
          .toMap
        buffer ++= data
        buffer
      }
    }
  }

  private def saveToFile(idx: Repr): Unit = {
    Using.resource(new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile.toFile)))) { stream =>
      stream.writeInt(INDEX_VERSION)
      stream.writeInt(idx.size)
      val values = idx.values
      val paths           = values.map(x => ideaRoot.relativize(x.installPath).toString).toArray
      val descriptorsStr  = values.map(_.descriptor.toXMLStr).toArray
      val fileNames       = values.map(_.downloadedFileName).toArray
      stream.writeObject(paths)
      stream.writeObject(descriptorsStr)
      stream.writeObject(fileNames)
    }
  }

  private def buildFromPluginsDir: Map[PluginId, PluginInfo] = {
    val pluginDirs = Files.list(ideaRoot.resolve("plugins")).collect(Collectors.toList[Path]).asScala.filter { file =>
      //extra filtering of unexpected extensions (e.g., some strange file plugin-classpath.txt)
      file.isDir || file.toString.endsWith(".jar")
    }
    pluginDirs.flatMap { pluginDir =>
      val pluginMetaData = extractPluginMetaData(pluginDir)
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

  private class WrongIndexVersionException(fileIndex: Int)
    extends RuntimeException(s"Index version in file $fileIndex is different from current $INDEX_VERSION")

  @TestOnly val INDEX_FILENAME = "plugins.idx"
  private val INDEX_VERSION = 3
  private val FILE_NAMES_SUPPORT_VERSION = 3
}
