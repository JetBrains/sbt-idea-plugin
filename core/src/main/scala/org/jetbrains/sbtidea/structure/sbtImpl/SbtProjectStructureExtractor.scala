package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.PluginLogger
import sbt.ProjectRef
import sbt.jetbrains.ideaPlugin.apiAdapter._


class SbtProjectStructureExtractor(override val rootProject: ProjectRef,
                                   override val projectsData: Seq[SbtProjectData],
                                   override val buildDependencies: BuildDependencies,
                                   _log: PluginLogger) extends SbtProjectStructureExtractorBase {
  override type ProjectDataType = SbtProjectData
  override type NodeType        = SbtProjectNodeImpl

  override implicit val log: PluginLogger = _log

  override def buildStub(data: SbtProjectData): SbtProjectNodeImpl = SbtProjectNodeImpl(data.thisProject, null, null, null)

  override def updateNode(node: SbtProjectNodeImpl, data: SbtProjectData): SbtProjectNodeImpl = {
    val children = collectChildren(node, data)
    val parents = collectParents(node, data)
    val libs = collectLibraries(data)
    node.children = children
    node.parents = parents
    node.libs = libs
    node
  }
}



//class SbtProjectStructureExtractor_AAAAAA(private val rootProject: ProjectRef,
//                                   private val projectsData: Seq[SbtProjectData],
//                                   private val buildDependencies: BuildDependencies)(implicit log: PluginLogger)
//  extends ProjectStructureExtractor with Ref2Node {
//
//  case class SbtProjectPackagingOptions(override val packageMethod: PackagingMethod,
//                                        override val libraryMappings: Seq[(ModuleKey, Option[String])],
//                                        override val fileMappings: Seq[(File, String)],
//                                        override val shadePatterns: Seq[ShadePattern],
//                                        override val excludeFilter: ExcludeFilter,
//                                        override val classRoots: Seq[File],
//                                        override val assembleLibraries: Boolean,
//                                        override val additionalProjects: Seq[ProjectNode]) extends ProjectPackagingOptions
//
//  override type ProjectDataType = SbtProjectData
//
//  private val projectMap: Map[ProjectRef, SbtProjectData] =
//    projectsData.iterator.map(x => x.thisProject -> mkProjectData(x)).toMap
//
//  private val revProjectMap: Seq[(ProjectRef, ProjectRef)] =
//    projectsData.flatMap(x => buildDependencies.classpathRefs(x.thisProject).map(_ -> x.thisProject))
//
//  private val projectCache: mutable.Map[ProjectRef, SbtProjectNodeImpl] =
//    new mutable.HashMap[ProjectRef, SbtProjectNodeImpl]
//
//
//  override def findProjectRef(project: Project): Option[ProjectRef] = projectMap.find(_._1.project == project.id).map(_._1)
//
//  override def getNode(ref: ProjectRef): ProjectNode = projectCache(ref)
//
//  private def buildNodeStub(ref:ProjectRef): SbtProjectNode = SbtProjectNode(ref, null, null, null, null).cache
//
//  private def validateProjectData(data: SbtProjectData): Unit = {
//    val unresolvedRefs = data.additionalProjects.map(x => x -> findProjectRef(x)).filter(_._2.isEmpty)
//    if (unresolvedRefs.nonEmpty)
//      throw new SbtProjectExtractException(s"Failed to resolve refs for projects: $unresolvedRefs")
//
//    val unmappedProjects = data.additionalProjects.flatMap(findProjectRef).map(x => x -> projectCache.get(x)).filter(_._2.isEmpty)
//    if (unmappedProjects.nonEmpty)
//      throw new SbtProjectExtractException(s"No stubs for project refs found: ${unmappedProjects.map(_._1)}")
//  }
//
//  private def extractPackagingOptions(ref: ProjectRef): ProjectPackagingOptions = {
//    val data = projectMap(ref)
//    implicit val ref2Node: Ref2Node = this
//    implicit val scalaVersion: ProjectScalaVersion =
//      ProjectScalaVersion(data.definedDeps.find(_.name == "scala-library"))
//
//    validateProjectData(data)
//
//    SbtProjectPackagingOptions(
//      data.packageMethod,
//      data.libMapping.map(x=> x._1.key -> x._2),
//      data.additionalMappings,
//      data.shadePatterns,
//      data.excludeFilter,
//      data.productDirs,
//      data.assembleLibraries,
//      data.additionalProjects.map(x => projectCache(findProjectRef(x).get))
//    )
//  }
//
//  private def extractLibraries(ref: ProjectRef): Seq[Library] = {
//    val projectData = projectMap(ref)
//
//    implicit val scalaVersion: ProjectScalaVersion =
//      ProjectScalaVersion(projectData.definedDeps.find(_.name == "scala-library"))
//
//    val libraryExtractor = new IvyLibraryExtractor(projectData)
//    libraryExtractor.extract
//  }
//
//  private def createNodeStubs(root: ProjectRef): Seq[SbtProjectNodeImpl] = {
//    val sortedRefs = topoSortRefs(root).reverse
//    val nodeStubs = sortedRefs.map(buildNodeStub)
//    nodeStubs
//  }
//
//  private def buildUnprocessedStubs(): Unit = {
//    val unprocessedRefs = projectsData.map(_.thisProject).filterNot(projectCache.contains)
//    if (unprocessedRefs.nonEmpty)
//      log.info(s"building stubs for ${unprocessedRefs.size} weak-referenced refs: $unprocessedRefs")
//    unprocessedRefs
//      .map(buildNodeStub)
//      .foreach(updateNode)
//  }
//
//  private def updateNode(node: SbtProjectNodeImpl): SbtProjectNode = {
//    val childRefs = buildDependencies.classpathRefs(node.ref)
//    assert(childRefs.forall(projectCache.contains), s"Child stubs incomplete: ${childRefs.filterNot(projectCache.contains)}")
//    node.children = childRefs.map(projectCache)
//
//    val parentRefs = revProjectMap.filter(_._1 == node.ref).map(_._2).distinct
//    assert(parentRefs.forall(projectCache.contains), s"Parent stubs incomplete: ${parentRefs.filterNot(projectCache.contains)}")
//    node.parents = parentRefs.map(projectCache)
//
//    val libs = extractLibraries(node.ref)
////    val packageOptions = extractPackagingOptions(node.ref)
//
//    node.libs = libs
////    node.packagingOptions = packageOptions
//
//    node
//  }
//
//  private def buildNodeGraph(sortedStubs: Seq[SbtProjectNodeImpl]): Seq[SbtProjectNodeImpl] = {
//    sortedStubs.map(updateNode)
//  }
//
//  private def topoSortRefs(root: ProjectRef, queue: Seq[ProjectRef] = Seq.empty): Seq[ProjectRef] = {
//    val data = projectMap(root)
//    if (!queue.contains(root)) {
//      val newQueue = queue :+ root
//      val direct = buildDependencies.classpathRefs(root).foldLeft(newQueue) { case (q, r) => topoSortRefs(r, q) }
//      val additional = data.additionalProjects.flatMap(findProjectRef).foldLeft(direct) { case (q, r) => topoSortRefs(r, q) }
//      additional
//    } else { queue }
//  }
//
//  private def mkProjectData(projectData: SbtProjectData): SbtProjectData = projectData
//
//  override def extract: Seq[SbtProjectNodeImpl] = {
//    log.info(s"building node stubs from root: $rootProject")
//    val stubs = createNodeStubs(rootProject)
//    buildUnprocessedStubs()
//    log.info(s"building node graph from nodes: $stubs")
//    val updatedNodes = buildNodeGraph(stubs)
//    updatedNodes
//  }
//}
