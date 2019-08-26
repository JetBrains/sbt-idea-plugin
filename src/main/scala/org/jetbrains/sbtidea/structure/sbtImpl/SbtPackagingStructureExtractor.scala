package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.packaging.ProjectPackagingOptions
import sbt.ProjectRef
import sbt.jetbrains.ideaPlugin.apiAdapter._



class SbtPackagingStructureExtractor(override val rootProject: ProjectRef,
                                     override val projectsData: Seq[SbtPackageProjectData],
                                     override val buildDependencies: BuildDependencies,
                                     _log: PluginLogger) extends SbtProjectStructureExtractorBase {
  override type ProjectDataType = SbtPackageProjectData
  override type NodeType        = SbtPackagedProjectNodeImpl

  override implicit val log: PluginLogger = _log

    private def validateProjectData(data: SbtProjectData): Unit = {
      val unresolvedRefs = data.additionalProjects.map(x => x -> findProjectRef(x)).filter(_._2.isEmpty)
      if (unresolvedRefs.nonEmpty)
        throw new SbtProjectExtractException(s"Failed to resolve refs for projects: $unresolvedRefs")

      val unmappedProjects = data.additionalProjects.flatMap(findProjectRef).map(x => x -> projectCache.get(x)).filter(_._2.isEmpty)
      if (unmappedProjects.nonEmpty)
        throw new SbtProjectExtractException(s"No stubs for project refs found: ${unmappedProjects.map(_._1)}")
    }

  private def collectPackagingOptions(data: SbtPackageProjectData): ProjectPackagingOptions = {
        val data = projectMap(ref)
        implicit val ref2Node: Ref2Node = this
        implicit val scalaVersion: ProjectScalaVersion =
          ProjectScalaVersion(data.definedDeps.find(_.name == "scala-library"))

        validateProjectData(data)

    ProjectPackagingOptions(
          data.packageMethod,
          data.libMapping.map(x=> x._1.key -> x._2),
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
}