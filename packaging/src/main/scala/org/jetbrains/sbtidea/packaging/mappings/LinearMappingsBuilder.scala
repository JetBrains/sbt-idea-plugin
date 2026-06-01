package org.jetbrains.sbtidea.packaging.mappings

import org.jetbrains.sbtidea.packaging.structure.{PackagedProjectNode, PackagingMethod}
import org.jetbrains.sbtidea.packaging.{MAPPING_KIND, Mapping, Mappings}
import org.jetbrains.sbtidea.structure.ProjectNode
import org.jetbrains.sbtidea.structure.sbtImpl.SbtProjectNode
import org.jetbrains.sbtidea.{PluginLogger, structure}
import sbt.*

import scala.collection.mutable

class LinearMappingsBuilder(
  override val outputDir: File,
  log: PluginLogger,
  rootProjectRef: Option[ProjectRef] = None
) extends AbstractMappingBuilder {

  class MappingBuildException(message: String) extends Exception(message)

  private val mappingsBuffer: mutable.Set[Mapping] = new mutable.TreeSet[Mapping]()

  private def processNode(
    node: PackagedProjectNode,
    libraryMappingsContext: LibraryMappingsContext
  ): Unit = {
    if (shouldSkip(node))
      return
    val targetJar = processTarget(node)
    validateLibraryMappings(node, libraryMappingsContext)
    processLibraries(node, targetJar, libraryMappingsContext.rootLibraryMappings)
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

      // note that we do not package into parent with PackagingMethod.PluginModule. For this explicitly use PluginModule
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

  /**
    * Emits a warning when an implicit merge target can look ambiguous.
    *
    * This method does not select the merge target: `to` is already resolved by `processTarget/findParentToMerge`.
    * Its only responsibility is to explain potentially surprising implicit merges and suggest an explicit
    * `MergeIntoOther(...)` override.
    *
    * Warning policy:
    *  - `MergeIntoOther(...)`: no warning, because the target is explicitly chosen by the user.
    *  - `MergeIntoParent()`: warn only when there are standalone candidates outside the selected target lineage.
    *    Standalone ancestors of `to` are treated as part of the same branch and are ignored.
    */
  private def validateMerge(from: PackagedProjectNode, to: PackagedProjectNode): Unit = {
    from.packagingOptions.packageMethod match {
      // Explicit target: the user already intentionally resolves ambiguity.
      case PackagingMethod.MergeIntoOther(_) =>
      case PackagingMethod.MergeIntoParent() =>
        // "Non-terminal" means `to` has non-skipped/non-deps-only ancestors.
        // For terminal targets there is nothing to warn about.
        if (to.hasRealParents) {
          val standaloneParentsTo = to.collectStandaloneParents
          val standaloneParentsFrom = from.collectStandaloneParents

          // Candidates on the selected target lineage are not true alternatives.
          // Keep only standalone ancestors that belong to other branches.
          val otherCandidates = standaloneParentsFrom.toSet -- standaloneParentsTo.toSet - to

          // Remaining candidates represent genuinely different standalone merge branches.
          if (otherCandidates.nonEmpty)
            log.warn(
              s"""Warning: $from will be merged into non-terminal $to, other candidates were: $otherCandidates
                 |You can specify explicit merge: packageMethod := PackagingMethod.MergeIntoOther(${otherCandidates.head.name})""".stripMargin
            )
        }
      case _ =>  // No merge warning logic is needed for other packaging methods.
    }
  }

  private def addProductDirs(from: PackagedProjectNode, to: File, isStatic: Boolean = true): File = {
    val metaData = from.mmd.copy(static = isStatic, kind = MAPPING_KIND.TARGET)
    from.packagingOptions.classRoots.foreach {
      mappingsBuffer += Mapping(_, to, metaData)
    }
    to
  }

  private case class LibraryMappingsContext(
    rootNode: Option[PackagedProjectNode],
    rootLibraryMappings: Map[structure.ModuleKey, Option[String]],
    allNodes: Seq[PackagedProjectNode]
  )

  private def validateLibraryMappings(node: PackagedProjectNode, context: LibraryMappingsContext): Unit = {
    val mappingsToValidate = node.packagingOptions.libraryMappings.toMap

    // See https://github.com/JetBrains/sbt-idea-plugin/pull/147
    val libsToValidateAgainst: Seq[structure.Library] =
      if (context.rootNode.contains(node))
        context.allNodes.flatMap(_.libs)
      else
        node.libs

    val invalidMappings = mappingsToValidate.filterNot { case (key, _) =>
      // Default filtering was added in https://github.com/JetBrains/sbt-idea-plugin/issues/75
      isDefaultScalaMapping(key) ||
        libsToValidateAgainst.exists(_.key == key)
    }
    invalidMappings.foreach { m =>
      log.fatal(s"No library dependencies match mapping $m in module ${node.name}")
    }
  }
  private def isDefaultScalaMapping(key: structure.ModuleKey): Boolean =
    key.org == "org.scala-lang.modules" ||
      key.org == "org.scala-lang"

  private def processLibraries(node: PackagedProjectNode, targetJar: File,
                               rootLibraryMappings: Map[structure.ModuleKey, Option[String]]): Unit = {
    def mapping(jarFile: File, to: File): Mapping =
      if (node.packagingOptions.assembleLibraries)
        Mapping(jarFile, targetJar, node.mmd.copy(kind = MAPPING_KIND.LIB_ASSEMBLY))
      else
        Mapping(jarFile, to, node.mmd.copy(kind = MAPPING_KIND.LIB))

    // Merge root project's libraryMappings as global defaults with this node's own mappings.
    // Node-specific mappings override root mappings (node wins via ++ ordering).
    //
    // | Node libMapping              | Root libMapping      | Merged (root ++ node)        | Result          |
    // |------------------------------|----------------------|------------------------------|-----------------|
    // | empty (external project)     | scala-.* -> None     | scala-.* -> None             | Scala excluded  |
    // | scala-.* -> None (subproject)| scala-.* -> None     | scala-.* -> None             | Same            |
    // | scala-reflect -> Some("lib/")| scala-.* -> None     | scala-reflect -> Some("lib/")| Node wins       |
    // | empty (no exclusions wanted) | empty                | empty                        | All included    |
    val mappings: Map[structure.ModuleKey, Option[String]] =
      rootLibraryMappings ++ node.packagingOptions.libraryMappings.toMap

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
    val libraryMappingsContext = buildLibraryMappingsContext(nodes)
    nodes.foreach(processNode(_, libraryMappingsContext))
    mappingsBuffer.toSeq
  }

  /**
    * Returns the root plugin project's `libraryMappings`, used by [[processLibraries]] as global
    * defaults applied to every node.
    *
    * This lets a plugin author set `packageLibraryMappings` once on the root project and have it
    * apply to external projects loaded via `dependsOn(RootProject(...))`, which don't run
    * sbt-idea-plugin and therefore carry an empty `libraryMappings`.
    */
  private def buildLibraryMappingsContext(nodes: Seq[PackagedProjectNode]): LibraryMappingsContext = {
    val rootNode = rootProjectNodeOf(nodes)
    LibraryMappingsContext(
      rootNode = rootNode,
      rootLibraryMappings = rootNode.map(_.packagingOptions.libraryMappings.toMap).getOrElse(Map.empty),
      allNodes = nodes
    )
  }

  private def rootProjectNodeOf(nodes: Seq[PackagedProjectNode]): Option[PackagedProjectNode] =
    rootProjectRef
      .flatMap(findRootProjectNodeByRef(nodes))
      .orElse(findRootProjectNodeByTopology(nodes))

  private def findRootProjectNodeByRef(nodes: Seq[PackagedProjectNode])(rootRef: ProjectRef): Option[PackagedProjectNode] =
    nodes.collectFirst {
      case node: SbtProjectNode if node.ref == rootRef =>
        node.asInstanceOf[PackagedProjectNode]
    }

  private def findRootProjectNodeByTopology(nodes: Seq[PackagedProjectNode]): Option[PackagedProjectNode] = {
    val rootCandidates = nodes.filter(_.parents.isEmpty)
    rootCandidates match {
      case Seq(root) => Some(root)
      case _ =>
        val standalone = rootCandidates.filter(_.packagingOptions.packageMethod.isInstanceOf[PackagingMethod.Standalone])
        standalone match {
          case Seq(root) => Some(root)
          case _         => None
        }
    }
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
