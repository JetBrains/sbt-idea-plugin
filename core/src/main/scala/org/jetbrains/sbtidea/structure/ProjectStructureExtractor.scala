package org.jetbrains.sbtidea.structure

trait ProjectStructureExtractor {
  type NodeType <: ProjectNode
  type ProjectDataType

  def collectChildren(node: NodeType, data: ProjectDataType): Seq[NodeType]
  def collectParents(node: NodeType, data: ProjectDataType): Seq[NodeType]
  def collectLibraries(data: ProjectDataType): Seq[Library]
  def buildStub(data: ProjectDataType): NodeType
  def updateNode(node: NodeType, data: ProjectDataType) : NodeType
  def extract: Seq[NodeType]
}
