package org.jetbrains.sbtidea.packaging.structure.sbtImpl

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.packaging
import org.jetbrains.sbtidea.packaging.structure
import org.jetbrains.sbtidea.packaging.structure.ProjectPackagingOptions
import org.jetbrains.sbtidea.structure.sbtImpl._
import sbt._
import sbt.jetbrains.ideaPlugin.apiAdapter._

import scala.language.implicitConversions

class SbtPackagingStructureExtractor(override val rootProject: ProjectRef,
                                     override val projectsData: Seq[SbtPackageProjectData],
                                     override val buildDependencies: BuildDependencies,
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
    implicit val scalaVersion: ProjectScalaVersion = ProjectScalaVersion(data.definedDeps.find(_.name == "scala-library"))

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

  override def buildStub(data: SbtPackageProjectData): SbtPackagedProjectNodeImpl =
    SbtPackagedProjectNodeImpl(data.thisProject, null, null, null, null)

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