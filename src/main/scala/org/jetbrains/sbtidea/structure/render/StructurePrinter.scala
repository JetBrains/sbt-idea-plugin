package org.jetbrains.sbtidea.structure.render

import com.github.mdr.ascii.graph.Graph
import com.github.mdr.ascii.layout
import org.jetbrains.sbtidea.structure.ProjectNode

import scala.collection.mutable

class StructurePrinter(private val verbose: Boolean = false) {

  private val vertices = mutable.HashSet[ProjectNode]()
  private val edges    = mutable.ArrayBuffer[(ProjectNode, ProjectNode)]()
  private val strategy = new ProjectNodeRenderingStrategy(verbose)

  private def buildGraphNodes(node: ProjectNode): Unit = {
    vertices += node
    edges ++= node.children.map(node -> _)
    node.children.foreach(buildGraphNodes)
  }

  def renderASCII(root: ProjectNode): String = {
    buildGraphNodes(root)
    val graph = Graph(vertices.toSet, edges.toList)
    layout.GraphLayout.renderGraph(graph, vertexRenderingStrategy = strategy)
  }

}
