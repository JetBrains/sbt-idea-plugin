package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.PluginLogger
import sbt.ProjectRef
import sbt.internal.BuildDependencies

class SbtProjectStructureExtractor(override val rootProject: ProjectRef,
                                   override val projectsData: Seq[SbtProjectData],
                                   override val buildDependencies: BuildDependencies,
                                   _log: PluginLogger) extends SbtProjectStructureExtractorBase {
  override type ProjectDataType = SbtProjectData
  override type NodeType        = SbtProjectNodeImpl

  override implicit val log: PluginLogger = _log

  override def buildStub(data: SbtProjectData): SbtProjectNodeImpl = SbtProjectNodeImpl(data.thisProject, data.thisProjectName, null, null, null)

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