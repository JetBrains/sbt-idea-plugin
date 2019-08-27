package org.jetbrains.sbtidea.packaging.artifact

import java.io.File
import java.nio.file.Path

import org.jetbrains.sbtidea.packaging._

import scala.collection.mutable

class IdeaArtifactXmlBuilder(artifactName: String, root: File) extends MappingArtifactBuilder[String] {
  import org.jetbrains.sbtidea.packaging.artifact.IdeaArtifactXmlBuilder._

  private implicit val rootPath: Path = root.toPath

  private val rootNode = Dir(mutable.ArrayBuffer.empty, "")

  override protected def copySingleJar(mapping: Mapping): Unit = {
    val toPath = mapping.to.toPath
    val fromPath = mapping.from.toPath
    val node = findOrCreateDirNode(mapping.entry)
    if (fromPath.getFileName == toPath.getFileName)
      node.children += CopyFile(fromPath)
    else
      node.children += CopyRenameJar(fromPath, toPath.getFileName.toString)
  }

  override protected def copyDirs(mappings: Mappings): Unit = {
    mappings.foreach { m =>
      val node = findOrCreateDirNode(m.entry)
      if (m.from.isDirectory)
        node.children += CopyDir(m.from.toPath)
      else
        node.children += CopyFile(m.from.toPath)
    }
  }

  override protected def packageJar(to: Path, mappings: Mappings): Unit = {
    assert(mappings.nonEmpty, s"Jar file should have at least one source defined: $to")

    val node = findOrCreateDirNode(mappings.head.entry)
    val nodes = mappings.map {
      case Mapping(_, _, MappingMetaData(_, _, _, Some(projectName), MAPPING_KIND.TARGET)) =>
        ModuleContent(projectName)
      case Mapping(from, _, MappingMetaData(_, _, _, _, MAPPING_KIND.LIB)) =>
        ExtractJar(from.toPath)
      case Mapping(from, _, MappingMetaData(_, _, _, _, MAPPING_KIND.LIB_ASSEMBLY)) =>
        ExtractJar(from.toPath)
      case other if other.from.isDirectory =>
        CopyDir(other.from.toPath)
      case other =>
        CopyFile(other.from.toPath)
    }.reverse   // JPS artifact builder only copies the first one from a list of equally named files
    node.children += PackageJar(nodes, to.getFileName.toString)
  }


  private def findOrCreateDirNode(path: String): Dir = {
    val elems = path.split("[/\\\\]")
    val result = elems.foldLeft(rootNode) {
      case (node , dirName) =>
        val child = node.children.collectFirst {
          case d@Dir(_, name) if name == dirName => d
        }
        child match {
          case Some(value) => value
          case None =>
            val newChild = Dir(mutable.ArrayBuffer.empty, dirName)
            node.children += newChild
            newChild
        }
    }
    result
  }

  private def render(entry: Entry): String = entry match {
    case Dir(children, name) =>
      s"""
         |<element id="directory" name="$name">
         |${children.map(render).mkString("\n")}
         |</element>
       """.stripMargin
    case PackageJar(from, name) =>
      s"""
         |<element id="archive" name="$name">
         |${from.map(render).mkString("\n")}
         |</element>
       """.stripMargin
    case CopyRenameJar(from, newName) =>
      s"""
         |<element id="archive" name="$newName">
         |<element id="extracted-dir" path="$from" path-in-jar="/" />
         |</element>
       """.stripMargin
    case CopyFile(from) =>
      s"""<element id="file-copy" path="$from" />"""
    case CopyDir(from) =>
      s"""<element id="dir-copy" path="$from" />"""
    case ModuleContent(name) =>
      s"""<element id="module-output" name="$name" />"""
    case ExtractJar(from) =>
      s"""<element id="extracted-dir" path="$from" path-in-jar="/" />"""
    case _ =>
      ""
  }

  override protected def createResult: String =
    s"""<component name="ArtifactManager">
       |  <artifact name="$artifactName">
       |    <output-path>$rootPath</output-path>
       |    <root id="root">
       |    ${rootNode.children.map(render).mkString("\n")}
       |    </root>
       |  </artifact>
       |</component>
     """.stripMargin

  override protected def mappingFilter(m: Mapping): Boolean = true
}

object IdeaArtifactXmlBuilder {
  implicit class MappingExt(val mapping: Mapping) extends AnyVal {
    def rel  (implicit root: Path): Path = root.relativize(mapping.to.toPath)
    def entry(implicit root: Path): String = Option(rel(root).getParent).map(_.toString).getOrElse("")
  }

  sealed trait Entry
  trait JarSource extends Entry
  case class Dir(children: mutable.ArrayBuffer[Entry], name: String) extends Entry
  case class CopyFile(from: Path) extends JarSource
  case class PackageJar(from: Seq[JarSource], name: String) extends Entry
  case class CopyRenameJar(from: Path, newName: String) extends Entry
  case class CopyDir(from: Path) extends JarSource
  case class ModuleContent(name: String) extends JarSource
  case class ExtractJar(from: Path) extends JarSource
}
