package org.jetbrains.sbtidea.packaging.structure.sbtImpl

import org.jetbrains.sbtidea.packaging.structure
import org.jetbrains.sbtidea.packaging.structure.ProjectPackagingOptions
import org.jetbrains.sbtidea.structure.ProjectScalaVersion
import org.jetbrains.sbtidea.structure.sbtImpl.*
import org.jetbrains.sbtidea.{PluginLogger, packaging}
import sbt.*
import sbt.internal.{BuildDependencies, BuildStructure}

import scala.language.implicitConversions

/**
 * @param rootProject in the case of projects which consist of multiple builds, this variable describes the root of all roots
 */
class SbtPackagingStructureExtractor(override val rootProject: ProjectRef,
                                     override val projectsData: Seq[SbtPackageProjectData],
                                     override val buildDependencies: BuildDependencies,
                                     val buildStructure: BuildStructure,
                                     _log: PluginLogger) extends SbtProjectStructureExtractorBase {
  override type ProjectDataType = SbtPackageProjectData
  override type NodeType        = SbtPackagedProjectNodeImpl

  override implicit val log: PluginLogger = _log

  private def validateProjectData(data: SbtPackageProjectData): Unit = {
    val unresolvedRefs = data.additionalProjects.map(x => x -> findProjectRef(x)).filter(_._2.isEmpty)
    if (unresolvedRefs.nonEmpty)
      throw new SbtProjectExtractException(s"Failed to resolve refs for projects: $unresolvedRefs")

    val unmappedProjects = data.additionalProjects.flatMap(findProjectRef).map(x => x -> projectCache.get(x)).filter(_._2.isEmpty)
    if (unmappedProjects.nonEmpty)
      throw new SbtProjectExtractException(s"No stubs for project refs found: ${unmappedProjects.map(_._1)}")
  }


  override protected def collectAdditionalProjects(data: SbtPackageProjectData, direct: Seq[ProjectRef]): Seq[ProjectRef] =
    data.additionalProjects.flatMap(findProjectRef).foldLeft(direct) { case (q, r) => topoSortRefs(r, q) }

  private def collectPackagingOptions(data: SbtPackageProjectData): ProjectPackagingOptions = {
    implicit val scalaVersion: ProjectScalaVersion = ProjectScalaVersionImpl(data.definedDeps.find(_.name == "scala-library"))

    validateProjectData(data)

    SbtProjectPackagingOptionsImpl(
      data.packageMethod,
      data.libMapping.map(x => x._1.key -> x._2),
      data.libraryBaseDir,
      data.additionalMappings,
      data.shadePatterns,
      data.excludeFilter,
      data.productDirs,
      data.assembleLibraries,
      data.additionalProjects.map(x => projectCache(findProjectRef(x).get))
    )
  }

  override def buildStub(data: SbtPackageProjectData): SbtPackagedProjectNodeImpl = {
    val parentName =
      if (isGroupingWithQualifiedNamesEnabled) {
        val rootData = findRootProjectDataInTheProjectBuild(data.thisProject)
        val isRootProject = data.thisProject.project == rootData.thisProject.project
        // note: it is important to take thisProjectName from rootData, because in the scala plugin the name of the root module is generated
        // from the root project name and not from the root project id (see SbtProjectResolver#createBuildProjectGroups)
        if (!isRootProject) Some(rootData.thisProjectName) else None
      } else {
        None
      }
    SbtPackagedProjectNodeImpl(data.thisProject, data.thisProjectName, parentName, null, null, null, null)
  }

  override def updateNode(node: SbtPackagedProjectNodeImpl, data: SbtPackageProjectData): SbtPackagedProjectNodeImpl = {
    val options = collectPackagingOptions(data)
    val children = collectChildren(node, data)
    val parents = collectParents(node, data)
    val libs = collectLibraries(data)
    node.packagingOptions = options
    node.children = children
    node.parents = parents
    node.libs = libs
    node
  }

  private def isGroupingWithQualifiedNamesEnabled: Boolean =
    sys.props.get("grouping.with.qualified.names.enabled").exists(_.toBoolean)


  /**
   *
   * @param projectRef project for which SbtPackageProjectData of its root project (within its build) is to be found
   */
  private def findRootProjectDataInTheProjectBuild(projectRef: ProjectRef): SbtPackageProjectData = {
    val rootProjectURI = projectRef.build
    val rootProjectId = buildStructure.rootProject(rootProjectURI)
    projectsData.find { data =>
      val projectRef = data.thisProject
      projectRef.build == rootProjectURI && projectRef.project == rootProjectId
    }.getOrElse(throw new RuntimeException(s"Failed to find root project name for $projectRef"))
  }

  /**
    * converts SBT-bound packaging.PackagingMethod into sbt-agnostic structure.PackagingMethod
    * by resolving sbt's Project ito an abstract ProjectNode
    */
  implicit def keys2Structure(p: packaging.PackagingMethod): structure.PackagingMethod = p match {
    case packaging.PackagingMethod.Skip() =>
      structure.PackagingMethod.Skip()
    case packaging.PackagingMethod.MergeIntoParent() =>
      structure.PackagingMethod.MergeIntoParent()
    case packaging.PackagingMethod.DepsOnly(targetPath) =>
      structure.PackagingMethod.DepsOnly(targetPath)
    case packaging.PackagingMethod.Standalone(targetPath, static) =>
      structure.PackagingMethod.Standalone(targetPath, static)
    case packaging.PackagingMethod.MergeIntoOther(project) =>
      structure.PackagingMethod.MergeIntoOther(findProjectRef(project).map(projectCache).getOrElse(???))
  }
}