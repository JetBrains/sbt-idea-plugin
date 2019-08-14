package org.jetbrains.sbtidea.tasks.packaging.structure.mappings

import sbt._
import org.jetbrains.sbtidea.tasks.packaging.structure.{Library, PackagingMethod, ProjectNode}
import org.jetbrains.sbtidea.tasks.packaging.{MAPPING_KIND, Mapping, Mappings}

import scala.collection.mutable

class LinearMappingsBuilder(override val outputDir: File) extends AbstractMappingBuilder {

  class MappingBuildException(message: String) extends Exception(message)

  private val mappingsBuffer: mutable.Set[Mapping] = new mutable.TreeSet[Mapping]()

  private def processNode(node: ProjectNode): Unit = {
    if (node.packagingOptions.packageMethod == PackagingMethod.Skip())
      return
    val targetJar = processTarget(node)
    processLibraries(node, targetJar)
    processFileMappings(node)
  }

  private def processTarget(node: ProjectNode): File = {
    node.packagingOptions.packageMethod match {
      case PackagingMethod.DepsOnly(targetPath) =>
        outputDir / targetPath
      case PackagingMethod.MergeIntoParent() =>
        val eligibleParentProject = findParentToMerge(node)
        val parentJar = mkProjectJarPath(eligibleParentProject)
        validateMerge(node, eligibleParentProject)
        addProductDirs(node, outputDir / parentJar)
      case PackagingMethod.MergeIntoOther(project) =>
        val eligibleParentProject = findParentToMerge(project)
        val otherJar = mkProjectJarPath(eligibleParentProject)
        validateMerge(node, eligibleParentProject)
        addProductDirs(node, outputDir / otherJar)
      case PackagingMethod.Standalone("", isStatic) =>
        val target = outputDir / mkProjectJarPath(node)
        addProductDirs(node, target, isStatic)
      case PackagingMethod.Standalone(targetPath, isStatic) =>
        val target = outputDir / targetPath
        addProductDirs(node, target, isStatic)
    }
  }

  private def findParentToMerge(node: ProjectNode): ProjectNode = {
    @scala.annotation.tailrec
    def collectCandidate(nodes: Seq[ProjectNode]): ProjectNode = {
      if (nodes.isEmpty)
        throw new MappingBuildException(s"No standalone-packaged parents found for $node")
      val candidates = nodes.filter(_.packagingOptions.packageMethod.isInstanceOf[PackagingMethod.Standalone]).distinct
      if (candidates.size > 1)
        throw new MappingBuildException(s"Multiple direct parents package into standalone jar(use MergeIntoOther): $candidates")
      if (candidates.size == 1)
        return candidates.head
      collectCandidate(nodes.flatMap(_.parents))
    }
    if (node.packagingOptions.packageMethod.isInstanceOf[PackagingMethod.Standalone])
      node
    else
      collectCandidate(node.parents)
  }

  private def validateMerge(from: ProjectNode, to: ProjectNode): Unit = {
    if (to.hasRealParents) {
      val otherCandidates = from.collectStandaloneParents.toSet - to
      if (otherCandidates.nonEmpty)
        println(
          s"""Warning: $from will be merged into non-terminal $to, other candidates were: $otherCandidates
             |You can specify explicit merge: packageMethod := PackagingMethod.MergeIntoOther(${otherCandidates.head.name})""".stripMargin
        )
    }
  }

  private def addProductDirs(from: ProjectNode, to: File, isStatic: Boolean = true): File = {
    val metaData = from.mmd.copy(static = isStatic, kind = MAPPING_KIND.TARGET)
    from.packagingOptions.classRoots.foreach {
      mappingsBuffer += Mapping(_, to, metaData)
    }
    to
  }

  private def processLibraries(node: ProjectNode, targetJar: File): Unit = {
    def mapping(lib: Library, to: File): Mapping =
      if (node.packagingOptions.assembleLibraries)
        Mapping(lib.jarFile, targetJar, node.mmd.copy(kind = MAPPING_KIND.LIB_ASSEMBLY))
      else
        Mapping(lib.jarFile, to, node.mmd.copy(kind = MAPPING_KIND.LIB))
    val mappings = node.packagingOptions.libraryMappings.toMap
    node.libs.foreach {
      case lib if !mappings.contains(lib.key) =>
        mappingsBuffer += mapping(lib, outputDir / mkRelativeLibPath(lib))
      case lib if mappings.getOrElse(lib.key, None).isDefined =>
        mappingsBuffer += mapping(lib, outputDir / mappings(lib.key).get)
      case _ =>
    }
  }

  private def processFileMappings(node: ProjectNode): Unit = {
    val metaData = node.mmd.copy(kind = MAPPING_KIND.MISC)
    node.packagingOptions.fileMappings.foreach { f =>
      mappingsBuffer += Mapping(f._1, outputDir / fixPaths(f._2), metaData)
    }
  }

  override def buildMappings(nodes: Seq[ProjectNode]): Mappings = {
    nodes.foreach(processNode)
    mappingsBuffer.toSeq
  }

  private def mkProjectJarPath(node: ProjectNode): String = s"lib/${node.name}.jar"

  private def mkRelativeLibPath(lib: Library) = s"lib/${lib.jarFile.getName}"

  private def fixPaths(str: String): String = System.getProperty("os.name") match {
    case os if os.startsWith("Windows") => str.replace('/', '\\')
    case _ => str.replace('\\', '/')
  }

}
