package org.jetbrains.sbtidea.download.plugin

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}

import org.jetbrains.sbtidea.TmpDirUtils
import org.jetbrains.sbtidea.packaging.artifact
import org.jetbrains.sbtidea._

import scala.collection.JavaConverters._

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
