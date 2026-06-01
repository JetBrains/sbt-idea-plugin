package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.structure.*
import sbt.internal.BuildDependencies
import sbt.{Project, ProjectRef}

import scala.collection.mutable

trait SbtProjectStructureExtractorBase extends ProjectStructureExtractor {
  override type ProjectDataType <: CommonSbtProjectData
  override type NodeType <: SbtProjectNode

  implicit val log: PluginLogger
  val rootProject: ProjectRef
  val buildDependencies: BuildDependencies
  val projectsData: Seq[ProjectDataType]

  /**
   * Maps each [[ProjectRef]] of the current build to its extracted project data.
   *
   * Only projects that belong to this build (i.e. present in `projectsData`) are included.
   * Projects pulled in via `dependsOn(RootProject(...))` live in their own builds and don't
   * run sbt-idea-plugin, so we have no data for them and they are deliberately absent here.
   * That is why every lookup against `projectMap` in this trait is guarded with
   * `projectMap.contains` / `projectMap.get`: an unguarded `projectMap(externalRef)` is exactly
   * the `NoSuchElementException` reported in issue #146.
   *
   * The filtering behaviour is covered by `SbtProjectStructureExtractorExternalRefsTest`.
   */
  protected lazy val projectMap: Map[ProjectRef, ProjectDataType] = projectsData.iterator.map(x => x.thisProject -> x).toMap
  // `filter(projectMap.contains)` drops reverse edges to external projects (dependsOn(RootProject(...))):
  // they are not in projectMap, so leaving them in would later crash collectParents' `projectMap(ref)`
  // lookup (the original #146 bug). See SbtProjectStructureExtractorExternalRefsTest.
  protected lazy val revProjectMap: Seq[(ProjectRef, ProjectRef)] = projectsData.flatMap(x => buildDependencies.classpathRefs(x.thisProject).filter(projectMap.contains).map(_ -> x.thisProject))
  protected lazy val projectCache: mutable.Map[ProjectRef, NodeType] = mutable.HashMap.empty

  def findProjectRef(project: Project): Option[ProjectRef] = projectMap.find(_._1.project == project.id).map(_._1)

  protected def topoSortRefs(root: ProjectRef, queue: Seq[ProjectRef] = Seq.empty): Seq[ProjectRef] = {
    projectMap.get(root) match {
      case None =>
        // `root` is an external project (dependsOn(RootProject(...))) that isn't part of this
        // build, so we have no data for it and can't place it in the graph — skip it. See #146.
        log.warn(s"skipping external project ref not part of the current build: $root")
        queue
      case Some(data) =>
        if (queue.contains(root)) queue
        else enqueueWithDependencies(data, root, queue)
    }
  }

  /**
   * Appends `root` to the topo-sort `queue`, then recursively visits its classpath
   * dependencies and any additional projects contributed by subclasses.
   *
   * External classpath refs are filtered out here for the same reason as in [[projectMap]]:
   * they belong to other builds and have no entry to recurse into.
   */
  private def enqueueWithDependencies(data: ProjectDataType, root: ProjectRef, queue: Seq[ProjectRef]): Seq[ProjectRef] = {
    val newQueue = queue :+ root
    val direct = buildDependencies.classpathRefs(root)
      .filter(projectMap.contains)
      .foldLeft(newQueue) { case (q, r) => topoSortRefs(r, q) }
    collectAdditionalProjects(data, direct)
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

    implicit val scalaVersion: ProjectScalaVersion = detectScalaVersion(projectData)

    val libraryExtractor = new IvyLibraryExtractor(projectData)
    libraryExtractor.extract
  }

  override def collectChildren(node: NodeType, data: ProjectDataType): Seq[NodeType] = {
    // Same rationale as revProjectMap/topoSortRefs: external classpath refs
    // (dependsOn(RootProject(...))) have no cached stub, so exclude them before lookup. See #146
    // and SbtProjectStructureExtractorExternalRefsTest.
    val childRefs = buildDependencies.classpathRefs(node.ref).filter(projectCache.contains)
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
