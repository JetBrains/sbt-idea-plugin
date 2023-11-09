package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.*
import org.jetbrains.sbtidea.packaging.artifact

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}
import scala.collection.JavaConverters.*

trait PluginMock extends TmpDirUtils {

  implicit class PluginMetaDataExt(metadata: PluginDescriptor) {
    def toPluginId: IntellijPlugin.Id = IntellijPlugin.Id(metadata.id, Some(metadata.version), None)
  }

  protected def createPluginJarMock(metaData: PluginDescriptor): Path = {
    val tmpDir = newTmpDir
    val targetPath = tmpDir.resolve(s"${metaData.name}.jar")
    val targetUri = URI.create("jar:" + targetPath.toUri)
    val opts = Map("create" -> "true").asJava
    artifact.using(FileSystems.newFileSystem(targetUri, opts)) { fs =>
      Files.createDirectory(fs.getPath("/", "META-INF"))
      Files.write(
        fs.getPath("/", "META-INF", "plugin.xml"),
        metaData.toXMLStr.getBytes
      )
    }
    targetPath
  }

  protected def createPluginZipMock(metaData: PluginDescriptor): Path = {
    val tmpDir = newTmpDir
    val targetPath = tmpDir.resolve(s"${metaData.name}.zip")
    val targetUri = URI.create("jar:" + targetPath.toUri)
    val opts = Map("create" -> "true").asJava

    val mainPluginJar = createPluginJarMock(metaData)

    artifact.using(FileSystems.newFileSystem(targetUri, opts)) { fs =>
      val libRoot = fs.getPath("/", metaData.name, "lib")
      Files.createDirectories(libRoot)
      Files.copy(
        mainPluginJar,
        libRoot.resolve(mainPluginJar.getFileName.toString)
      )
    }
    targetPath
  }
}
