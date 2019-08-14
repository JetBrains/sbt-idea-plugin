package org.jetbrains.sbtidea.tasks.packaging.structure.sbtImpl

import java.io.File

import org.jetbrains.sbtidea.Keys.PackagingMethod._
import org.jetbrains.sbtidea.tasks.packaging._
import org.jetbrains.sbtidea.tasks.packaging.artifact.ExcludeFilter.ExcludeFilter
import org.jetbrains.sbtidea.tasks.packaging.artifact.ShadePattern
import org.jetbrains.sbtidea.tasks.packaging.structure._
import sbt.jetbrains.ideaPlugin.apiAdapter._
import sbt.{Project, ProjectRef}

import scala.collection.mutable

class SbtProjectStructureExtractor(private val rootProject: ProjectRef,
                                   private val projectsData: Seq[ProjectData],
                                   private val buildDependencies: BuildDependencies)
  extends ProjectStructureExtractor with Ref2Node {


  case class SbtProjectNode(ref: ProjectRef,
                            var parents: Seq[ProjectNode],
                            var children: Seq[ProjectNode],
                            var libs: Seq[Library],
                            var packagingOptions: ProjectPackagingOptions) extends ProjectNode {
    def cache: SbtProjectNode = { projectCache += ref -> this; this }
  }

  case class SbtProjectPackagingOptions(override val packageMethod: PackagingMethod,
                                        override val libraryMappings: Seq[(ModuleKey, Option[String])],
                                        override val fileMappings: Seq[(File, String)],
                                        override val shadePatterns: Seq[ShadePattern],
                                        override val excludeFilter: ExcludeFilter,
                                        override val additionalProjects: Seq[ProjectNode]) extends ProjectPackagingOptions

  private val projectMap: Map[ProjectRef, ProjectData] =
    projectsData.iterator.map(x => x.thisProject -> mkProjectData(x)).toMap

  private val revProjectMap: Seq[(ProjectRef, ProjectRef)] =
    projectsData.flatMap(x => buildDependencies.classpathRefs(x.thisProject).map(_ -> x.thisProject))

  private val projectCache: mutable.Map[ProjectRef, ProjectNode] =
    new mutable.HashMap[ProjectRef, ProjectNode]

  override def findProjectRef(project: Project): Option[ProjectRef] = projectMap.find(_._1.project == project.id).map(_._1)

  override def getNode(ref: ProjectRef): ProjectNode = projectCache(ref)

  private def buildNodeStub(ref:ProjectRef): SbtProjectNode = SbtProjectNode(ref, null, null, null, null).cache

  private def validateProjectData(data: ProjectData): Unit = {
    val unresolvedRefs = data.additionalProjects.map(x => x -> findProjectRef(x)).filter(_._2.isEmpty)
    if (unresolvedRefs.nonEmpty)
      throw new SbtProjectExtractException(s"Failed to resolve refs for projects: $unresolvedRefs")

    val unmappedProjects = data.additionalProjects.flatMap(findProjectRef).map(x => x -> projectCache.get(x)).filter(_._2.isEmpty)
    if (unmappedProjects.nonEmpty)
      throw new SbtProjectExtractException(s"No stubs for project refs found: ${unmappedProjects.map(_._1)}")
  }

  private def extractPackagingOptions(ref: ProjectRef): ProjectPackagingOptions = {
    val data = projectMap(ref)
    implicit val ref2Node: Ref2Node = this
    implicit val scalaVersion: ProjectScalaVersion =
      ProjectScalaVersion(data.definedDeps.find(_.name == "scala-library"))

    validateProjectData(data)

    SbtProjectPackagingOptions(
      data.packageMethod,
      data.libMapping.map(x=> x._1.key -> x._2),
      data.additionalMappings,
      data.shadePatterns,
      data.excludeFilter,
      data.additionalProjects.map(x => projectCache(findProjectRef(x).get))
    )
  }

  private def extractLibraries(ref: ProjectRef): Seq[Library] = {
    val projectData = projectMap(ref)

    implicit val scalaVersion: ProjectScalaVersion =
      ProjectScalaVersion(projectData.definedDeps.find(_.name == "scala-library"))

    val libraryExtractor = new IvyLibraryExtractor(projectData)
    libraryExtractor.extract
  }

  private def createNodeStubs(root: ProjectRef): Seq[SbtProjectNode] = {
    val sortedRefs = topoSortRefs(root).reverse
    val nodeStubs = sortedRefs.map(buildNodeStub)
    nodeStubs
  }

  private def updateNode(node: SbtProjectNode): SbtProjectNode = {
    val childRefs = buildDependencies.classpathRefs(node.ref)
    assert(childRefs.forall(projectCache.contains), s"Child stubs incomplete: ${childRefs.filterNot(projectCache.contains)}")
    node.children = childRefs.map(projectCache)

    val parentRefs = revProjectMap.filter(_._1 == node.ref).map(_._2).distinct
    assert(parentRefs.forall(projectCache.contains), s"Parent stubs incomplete: ${parentRefs.filterNot(projectCache.contains)}")
    node.parents = parentRefs.map(projectCache)

    val libs = extractLibraries(node.ref)
    val packageOptions = extractPackagingOptions(node.ref)

    node.libs = libs
    node.packagingOptions = packageOptions

    node
  }

  private def buildNodeGraph(sortedStubs: Seq[SbtProjectNode]): Seq[ProjectNode] = {
    sortedStubs.map(updateNode)
  }

  private def topoSortRefs(root: ProjectRef, queue: Seq[ProjectRef] = Seq.empty): Seq[ProjectRef] = {
    val data = projectMap(root)
    if (!queue.contains(root)) {
      val newQueue = queue :+ root
      val direct = buildDependencies.classpathRefs(root).foldLeft(newQueue) { case (q, r) => topoSortRefs(r, q) }
      val additional = data.additionalProjects.flatMap(findProjectRef).foldLeft(direct) { case (q, r) => topoSortRefs(r, q) }
      additional
    } else { queue }
  }

  private def mkProjectData(projectData: ProjectData): ProjectData = {
    if (projectData.thisProject == rootProject && !projectData.packageMethod.isInstanceOf[Standalone]) {
      projectData.copy(packageMethod = Standalone())
    } else projectData
  }

  private def verifyNodeGraph(nodes: Seq[ProjectNode]): Unit = {
    //TODO
  }

  override def extract: Seq[ProjectNode] = {
    val stubs = createNodeStubs(rootProject)
    val updatedNodes = buildNodeGraph(stubs)
    verifyNodeGraph(updatedNodes)
    updatedNodes
  }
}
