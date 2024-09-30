package org.jetbrains.sbtidea.packaging.mappings

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.packaging.structure.{PackagedProjectNode, PackagingMethod}
import org.jetbrains.sbtidea.packaging.{MAPPING_KIND, Mapping, Mappings}
import org.jetbrains.sbtidea.structure.{Library, ProjectNode}
import sbt.*

import scala.collection.mutable
import scala.util.Try

class LinearMappingsBuilder(override val outputDir: File, log: PluginLogger) extends AbstractMappingBuilder {

  class MappingBuildException(message: String) extends Exception(message)

  private val mappingsBuffer: mutable.Set[Mapping] = new mutable.TreeSet[Mapping]()

  private def processNode(node: PackagedProjectNode): Unit = {
    if (shouldSkip(node))
      return
    val targetJar = processTarget(node)
    processLibraries(node, targetJar)
    processFileMappings(node)
  }

  private def shouldSkip(node: PackagedProjectNode): Boolean = {
    val res = node.packagingOptions.packageMethod == PackagingMethod.Skip()
    if (res && (
      node.packagingOptions.fileMappings.nonEmpty         ||
        node.packagingOptions.shadePatterns.nonEmpty      ||
        node.packagingOptions.additionalProjects.nonEmpty ||
        node.packagingOptions.assembleLibraries)) {
      log.warn(s"project $node is skipped, but has packaging options defined, did you mean PackagingMethod.DepsOnly()")
    }
    res
  }

  private def processTarget(node: PackagedProjectNode): File = {
    node.packagingOptions.packageMethod match {
      case PackagingMethod.DepsOnly(targetPath) =>
        outputDir / targetPath
      case PackagingMethod.MergeIntoParent() =>
        val eligibleParentProject = findParentToMerge(node)
        val parentJar = getTopLevelJarPath(eligibleParentProject)
        validateMerge(node, eligibleParentProject)
        addProductDirs(node, outputDir / parentJar)
      case PackagingMethod.MergeIntoOther(project) =>
        val eligibleParentProject = findParentToMerge(project)
        val otherJar = getTopLevelJarPath(eligibleParentProject)
        validateMerge(node, eligibleParentProject)
        addProductDirs(node, outputDir / otherJar)
      case PackagingMethod.Standalone("", isStatic) =>
        val target = outputDir / mkProjectJarDefaultPath(node)
        addProductDirs(node, target, isStatic)
      case PackagingMethod.Standalone(targetPath, isStatic) =>
        val target = outputDir / targetPath
        addProductDirs(node, target, isStatic)
      case PackagingMethod.Skip() => throw new MappingBuildException("Unreachable")
    }
  }

  private def findParentToMerge(node: PackagedProjectNode): PackagedProjectNode = {
    @scala.annotation.tailrec
    def collectCandidate(nodes: Seq[PackagedProjectNode]): PackagedProjectNode = {
      if (nodes.isEmpty)
        throw new MappingBuildException(s"No standalone-packaged parents found for $node")
      val candidates = nodes.filter(_.packagingOptions.packageMethod.isInstanceOf[PackagingMethod.Standalone]).distinct
      if (candidates.size > 1)
        throw new MappingBuildException(s"Multiple direct parents package into standalone jar ($node) (use MergeIntoOther): $candidates")
      if (candidates.size == 1)
        return candidates.head
      collectCandidate(nodes.flatMap(_.parents))
    }

    if (node.packagingOptions.packageMethod.isInstanceOf[PackagingMethod.Standalone]) {
      node
    } else {
      // We want to find all reachable parent nodes that have PackagingMethod.Standalone
      // and that are not reachable through another parent node with PackingMethod.Standalone
      // The value of the map below tracks whether there is another standalone node that
      // is between the key and `node`
      val standaloneParents = mutable.Map.empty[PackagedProjectNode, Boolean]

      def process(node: PackagedProjectNode, covered: Boolean): Unit = {
        val isStandalone = node.packagingOptions.packageMethod.isInstanceOf[PackagingMethod.Standalone]
        if (isStandalone) {
          val wasInitialized = standaloneParents.contains(node)

          standaloneParents(node) = covered || standaloneParents.getOrElse(node, false)

          if (wasInitialized) {
            // It doesn't matter whether this node was covered or not;
            // all parents have at least been covered by this node.
            // So we don't have to process them again
            return
          }
        }

        node.parents.foreach(process(_, covered || isStandalone))
      }

      process(node, covered = false)

      val candidates = standaloneParents.iterator.filter(_._2 == false).map(_._1).toSeq
      val candidate = candidates match {
        case Seq(one) => one
        case Seq() =>
          throw new MappingBuildException(s"No standalone-packaged parents found for $node")
        case _ =>
          val withoutModules = candidates.filter(_.packagingOptions.packageMethod match {
            case PackagingMethod.Standalone(_, _, true) => false
            case _ => true
          })

          withoutModules match {
            case Seq(one) => one
            case _ =>
              throw new MappingBuildException(
                s"Cannot determine a standalone parent package to merge into for $node, " +
                  s"Standalone candidates: ${candidates.sortBy(_.name).mkString(", ")}"
              )
          }
      }

      log.info(s"                     Merging $node into $candidate")
      log.info(s"Old method would have merged $node into ${Try(collectCandidate(node.parents)).fold(_.getMessage, _.toString)}")

      candidate
    }
  }

  private def validateMerge(from: PackagedProjectNode, to: PackagedProjectNode): Unit = {
    if (to.hasRealParents) {
      val otherCandidates = from.collectStandaloneParents.toSet - to
      if (otherCandidates.nonEmpty)
        log.warn(
          s"""Warning: $from will be merged into non-terminal $to, other candidates were: $otherCandidates
             |You can specify explicit merge: packageMethod := PackagingMethod.MergeIntoOther(${otherCandidates.head.name})""".stripMargin
        )
    }
  }

  private def addProductDirs(from: PackagedProjectNode, to: File, isStatic: Boolean = true): File = {
    val metaData = from.mmd.copy(static = isStatic, kind = MAPPING_KIND.TARGET)
    from.packagingOptions.classRoots.foreach {
      mappingsBuffer += Mapping(_, to, metaData)
    }
    to
  }

  private def processLibraries(node: PackagedProjectNode, targetJar: File): Unit = {
    def mapping(lib: Library, to: File): Mapping =
      if (node.packagingOptions.assembleLibraries)
        Mapping(lib.jarFile, targetJar, node.mmd.copy(kind = MAPPING_KIND.LIB_ASSEMBLY))
      else
        Mapping(lib.jarFile, to, node.mmd.copy(kind = MAPPING_KIND.LIB))
    val mappings = node.packagingOptions.libraryMappings.toMap
    val invalidMappings = mappings
      .filterNot { case (key, _) =>
        key.org   == "org.scala-lang.modules" || // filter out default mappings
          key.org == "org.scala-lang"         || // filter out default mappings
          node.libs.exists(_.key == key)
      }
    invalidMappings.foreach { m =>
      log.fatal(s"No library dependencies match mapping $m in module ${node.name}")
    }
    node.libs.foreach {
      case lib if !mappings.contains(lib.key) =>
        mappingsBuffer += mapping(lib, outputDir / s"${node.packagingOptions.libraryBaseDir}/${lib.jarFile.getName}")
      case lib if mappings.getOrElse(lib.key, None).isDefined =>
        mappingsBuffer += mapping(lib, outputDir / mappings(lib.key).get)
      case _ =>
    }
  }

  private def processFileMappings(node: PackagedProjectNode): Unit = {
    val metaData = node.mmd.copy(kind = MAPPING_KIND.MISC)
    node.packagingOptions.fileMappings.foreach { f =>
      mappingsBuffer += Mapping(f._1, outputDir / fixPaths(f._2), metaData)
    }
  }

  override def buildMappings(nodes: Seq[PackagedProjectNode]): Mappings = {
    log.info(s"building mappings for ${nodes.size} nodes")
    nodes.foreach(processNode)
    mappingsBuffer.toSeq
  }

  private def getTopLevelJarPath(node: PackagedProjectNode): String = node.packagingOptions.packageMethod match {
    case PackagingMethod.Skip() =>
      throw new MappingBuildException(s"$node cannot be a top-level project")
    case PackagingMethod.MergeIntoParent() =>
      throw new MappingBuildException(s"$node cannot be a top-level project")
    case PackagingMethod.MergeIntoOther(_) =>
      throw new MappingBuildException(s"$node cannot be a top-level project")
    case PackagingMethod.DepsOnly("") =>
      mkProjectJarDefaultPath(node)
    case PackagingMethod.DepsOnly(nonEmptyPath) =>
      nonEmptyPath
    case PackagingMethod.Standalone("", _) =>
      mkProjectJarDefaultPath(node)
    case PackagingMethod.Standalone(nonEmptyPath, _) =>
      nonEmptyPath
  }

  private def mkProjectJarDefaultPath(node: ProjectNode): String = s"lib/${node.name}.jar"

  private def fixPaths(str: String): String = System.getProperty("os.name") match {
    case os if os.startsWith("Windows") => str.replace('/', '\\')
    case _ => str.replace('\\', '/')
  }

}
