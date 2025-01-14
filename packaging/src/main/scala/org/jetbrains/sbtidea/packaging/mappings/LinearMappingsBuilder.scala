package org.jetbrains.sbtidea.packaging.mappings

import org.jetbrains.sbtidea.packaging.structure.{PackagedProjectNode, PackagingMethod}
import org.jetbrains.sbtidea.packaging.{MAPPING_KIND, Mapping, Mappings}
import org.jetbrains.sbtidea.structure.ProjectNode
import org.jetbrains.sbtidea.{PluginLogger, structure}
import sbt.*

import scala.collection.mutable

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
      case PackagingMethod.PluginModule(moduleName, isStatic) =>
        val target = outputDir / mkPluginModulePath(moduleName)
        addProductDirs(node, target, isStatic)
      case PackagingMethod.Skip() => throw new MappingBuildException("Unreachable")
    }
  }

  private def findParentToMerge(node: PackagedProjectNode): PackagedProjectNode = {
    @scala.annotation.tailrec
    def collectCandidate(nodes: Seq[PackagedProjectNode]): PackagedProjectNode = {
      if (nodes.isEmpty)
        throw new MappingBuildException(s"No standalone-packaged parents found for $node")

      // note that we do not package into parent with PackagingMethod.PluginModule. For this explicilty use PluginModule
      val candidates = nodes.filter(_.packagingOptions.packageMethod.isInstanceOf[PackagingMethod.Standalone]).distinct
      if (candidates.size > 1)
        throw new MappingBuildException(s"Multiple direct parents package into standalone jar ($node) (use MergeIntoOther): $candidates")
      if (candidates.size == 1)
        return candidates.head
      collectCandidate(nodes.flatMap(_.parents))
    }
    if (node.packagingOptions.packageMethod.isInstanceOf[PackagingMethod.Standalone])
      node
    else
      collectCandidate(node.parents)
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
    def mapping(jarFile: File, to: File): Mapping =
      if (node.packagingOptions.assembleLibraries)
        Mapping(jarFile, targetJar, node.mmd.copy(kind = MAPPING_KIND.LIB_ASSEMBLY))
      else
        Mapping(jarFile, to, node.mmd.copy(kind = MAPPING_KIND.LIB))

    val mappings: Map[structure.ModuleKey, Option[String]] =
      node.packagingOptions.libraryMappings.toMap

    val invalidMappings = mappings
      .filterNot { case (key, _) =>
        key.org == "org.scala-lang.modules" || // filter out default mappings
          key.org == "org.scala-lang"         || // filter out default mappings
          node.libs.exists(_.key == key)
      }
    invalidMappings.foreach { m =>
      log.fatal(s"No library dependencies match mapping $m in module ${node.name}")
    }

    for {
      lib <- node.libs
      jarFile <- lib.jarFiles
    } {
      // ATTENTION!
      // This code can't handle the case with multiple artifacts in the library with different classifiers
      // Current mapping can map only the whole module id but not separate artifacts with different classifiers
      // E.g. if user has ("org.lwjgl" % "lwjgl" % "3.3.6" % Runtime).classifier("natives-windows")
      // and wants to map only it to `lib/native` using `packageLibraryMappings` it won't work
      // (see https://github.com/JetBrains/sbt-idea-plugin/issues/135)
      // But so far it's ok, we don't have real examples when that would be really needed
      mappings.get(lib.key) match {
        case Some(None) => // to ignore the artifact, None means "don't package the library"
        case Some(Some(mappedLocation)) =>
          mappingsBuffer += mapping(jarFile, outputDir / mappedLocation)
        case _ =>
          mappingsBuffer += mapping(jarFile, outputDir / s"${node.packagingOptions.libraryBaseDir}/${jarFile.getName}")
      }
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
    case PackagingMethod.PluginModule(moduleName, _) =>
      mkPluginModulePath(moduleName)
  }

  private def mkProjectJarDefaultPath(node: ProjectNode): String = s"lib/${node.name}.jar"
  private def mkPluginModulePath(moduleName: String): String = s"lib/modules/$moduleName.jar"

  private def fixPaths(str: String): String = System.getProperty("os.name") match {
    case os if os.startsWith("Windows") => str.replace('/', '\\')
    case _ => str.replace('\\', '/')
  }

}
