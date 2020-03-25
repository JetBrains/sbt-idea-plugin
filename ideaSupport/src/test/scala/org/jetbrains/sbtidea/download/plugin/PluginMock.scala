package org.jetbrains.sbtidea.download.plugin

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}

import org.jetbrains.sbtidea.TmpDirUtils
import org.jetbrains.sbtidea.packaging.artifact
import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.download.plugin.PluginDescriptor.Dependency

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
        createPluginXmlContent(metaData).getBytes
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


  protected def createPluginXmlContent(metaData: PluginDescriptor): String = {
    val depStr = metaData.dependsOn.map {
      case Dependency(id, true)  => s"""<depends optional="true">$id</depends>"""
      case Dependency(id, false) => s"<depends>$id</depends>"
    }
    s"""
       |<idea-plugin>
       |  <name>${metaData.name}</name>
       |  <id>${metaData.id}</id>
       |  <version>${metaData.version}</version>
       |  <idea-version since-build="${metaData.sinceBuild}" until-build="${metaData.untilBuild}"/>
       |  ${depStr.mkString("\n")}
       |</idea-plugin>
       |""".stripMargin
  }

}
