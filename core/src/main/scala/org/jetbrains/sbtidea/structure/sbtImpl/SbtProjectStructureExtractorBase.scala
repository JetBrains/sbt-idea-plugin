package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.structure.*
import sbt.jetbrains.ideaPlugin.apiAdapter.*
import sbt.{ModuleID, Project, ProjectRef}

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

  protected def collectAdditionalProjects(data: ProjectDataType, direct: Seq[ProjectRef]): Seq[ProjectRef] = direct

  private def buildNodeGraph(sortedStubs: Seq[NodeType]): Seq[NodeType] = {
    sortedStubs.map(x => x -> projectMap(x.ref)).map { case (node, data) =>
      updateNode(node, data)
    }
  }

  private def buildUnprocessedStubs(): Seq[NodeType] = {
    val unprocessedProjectsData = projectsData.filterNot(x => projectCache.contains(x.thisProject))
    if (unprocessedProjectsData.nonEmpty)
      log.info(s"building stubs for ${unprocessedProjectsData.size} weak-referenced refs: ${unprocessedProjectsData.map(_.thisProject)}")
    unprocessedProjectsData
      .map(buildStub)
      .map { stub => projectCache += stub.ref -> stub; stub }
      .zip(unprocessedProjectsData)
      .map { case (t, data) => updateNode(t, data) }
  }

  private def createNodeStubsFromRoot(root: ProjectRef): Seq[NodeType] = {
    val sortedRefs = topoSortRefs(root).reverse
    val projectData = sortedRefs.map(projectMap)
    val nodeStubs = projectData.map(buildStub)
    nodeStubs.foreach(stub => projectCache += stub.ref -> stub)
    nodeStubs
  }

  override def collectLibraries(data: ProjectDataType): Seq[Library] = {
    val projectData = projectMap(data.thisProject)

    def isScalaLibrary(moduleId: ModuleID) = moduleId.name match {
      case "scala-library" | "scala3-library_3" => true
      case _ => false
    }

    implicit val scalaVersion: ProjectScalaVersion =
      ProjectScalaVersionImpl(
        projectData.cp
          .flatMap(_.metadata.get(sbt.Keys.moduleID.key))
          .find(isScalaLibrary)
          .orElse(projectData.definedDeps.find(isScalaLibrary))
      )

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
    val stubs = createNodeStubsFromRoot(rootProject)
    buildUnprocessedStubs()
    log.info(s"building node graph from nodes: $stubs")
    val updatedNodes = buildNodeGraph(stubs)
    updatedNodes
  }
}
