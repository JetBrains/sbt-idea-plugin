package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.structure._
import sbt.jetbrains.ideaPlugin.apiAdapter._
import sbt.{Project, ProjectRef}

import scala.collection.mutable

trait SbtProjectStructureExtractorBase extends ProjectStructureExtractor {
  override type ProjectDataType <: CommonSbtProjectData
  override type NodeType <: SbtProjectNode

  implicit val log: PluginLogger
  val rootProject: ProjectRef
  val buildDependencies: BuildDependencies
  val projectsData: Seq[ProjectDataType]

  protected lazy val projectMap: Map[ProjectRef, ProjectDataType] = projectsData.iterator.map(x => x.thisProject -> x).toMap
  protected lazy val revProjectMap: Seq[(ProjectRef, ProjectRef)] = projectsData.flatMap(x => buildDependencies.classpathRefs(x.thisProject).map(_ -> x.thisProject))
  protected lazy val projectCache: mutable.Map[ProjectRef, NodeType] = mutable.HashMap.empty

  def findProjectRef(project: Project): Option[ProjectRef] = projectMap.find(_._1.project == project.id).map(_._1)

  protected def topoSortRefs(root: ProjectRef, queue: Seq[ProjectRef] = Seq.empty): Seq[ProjectRef] = {
    val data = projectMap(root)
    if (!queue.contains(root)) {
      val newQueue = queue :+ root
      val direct = buildDependencies.classpathRefs(root).foldLeft(newQueue) { case (q, r) => topoSortRefs(r, q) }
      val additional = collectAdditionalProjects(data, direct)
      additional
    } else { queue }
  }

  protected def collectAdditionalProjects(data: ProjectDataType, direct: Seq[ProjectRef]): Seq[ProjectRef] = Seq.empty

  private def buildNodeGraph(sortedStubs: Seq[NodeType]): Seq[NodeType] = {
    sortedStubs.map(x => x -> projectMap(x.ref)).map { case (node, data) =>
      updateNode(node, data)
    }
  }

  private def buildUnprocessedStubs(): Seq[NodeType] = {
    val unprocessedProjectsData = projectsData.filterNot(x => projectCache.contains(x.thisProject))
    if (unprocessedProjectsData.nonEmpty)
      log.info(s"building stubs for ${unprocessedProjectsData.size} weak-referenced refs: $unprocessedProjectsData")
    unprocessedProjectsData
      .map(buildStub)
      .zip(unprocessedProjectsData)
      .map { case (t, data) => updateNode(t, data) }
  }

  private def createNodeStubs(root: ProjectRef): Seq[NodeType] = {
    val sortedRefs = topoSortRefs(root).reverse
    val projectData = sortedRefs.map(projectMap)
    val nodeStubs = projectData.map(buildStub)
    nodeStubs
  }

  override def collectLibraries(data: ProjectDataType): Seq[Library] = {
    val projectData = projectMap(data.thisProject)

    implicit val scalaVersion: ProjectScalaVersion =
      ProjectScalaVersion(projectData.definedDeps.find(_.name == "scala-library"))

    val libraryExtractor = new IvyLibraryExtractor(projectData)
    libraryExtractor.extract
  }

  override def collectChildren(node: NodeType, data: ProjectDataType): Seq[NodeType] = {
    val childRefs = buildDependencies.classpathRefs(node.ref)
    assert(childRefs.forall(projectCache.contains), s"Child stubs incomplete: ${childRefs.filterNot(projectCache.contains)}")
    childRefs.map(projectCache)
  }

  override def collectParents(node: NodeType, data: ProjectDataType): Seq[NodeType] = {
    val parentRefs = revProjectMap.filter(_._1 == node.ref).map(_._2).distinct
    assert(parentRefs.forall(projectCache.contains), s"Parent stubs incomplete: ${parentRefs.filterNot(projectCache.contains)}")
    parentRefs.map(projectCache)
  }

  override def extract: Seq[NodeType] = {
    log.info(s"building node stubs from root: $rootProject")
    val stubs = createNodeStubs(rootProject)
    buildUnprocessedStubs()
    log.info(s"building node graph from nodes: $stubs")
    val updatedNodes = buildNodeGraph(stubs)
    updatedNodes
  }
}
