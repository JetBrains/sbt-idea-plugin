package org.jetbrains.sbtidea.tasks.structure.render

import com.github.mdr.ascii.common.Dimension
import com.github.mdr.ascii.layout.coordAssign.VertexRenderingStrategy
import org.jetbrains.sbtidea.structure.ProjectNode

class ProjectNodeRenderingStrategy(verbose: Boolean) extends VertexRenderingStrategy[ProjectNode] {

  override def getPreferredSize(node: ProjectNode): Dimension = {
    val lines = splitLines(getNodeText(node))
    Dimension(lines.size, if (lines.isEmpty) 0 else lines.map(_.length).max)
  }

  override def getText(node: ProjectNode, allocatedSize: Dimension): List[String] = {
    val unpaddedLines =
      splitLines(getNodeText(node)).take(allocatedSize.height).map { line ⇒ centerLine(allocatedSize, line) }
    val verticalDiscrepancy = Math.max(0, allocatedSize.height - unpaddedLines.size)
    val verticalPadding = List.fill(verticalDiscrepancy / 2)("")
    verticalPadding ++ unpaddedLines ++ verticalPadding
  }

  private def splitLines(s: String): List[String] =
    s.split("(\r)?\n").toList match {
      case Nil | List("") ⇒ Nil
      case xs ⇒ xs
    }

  private def centerLine(allocatedSize: Dimension, line: String): String = {
    val discrepancy = allocatedSize.width - line.length
    val padding = " " * (discrepancy / 2)
    padding + line
  }

  private def getNodeText(node: ProjectNode): String = {
    if (verbose)
      s"""$node
         | ${node.libs}
         |""".stripMargin
    else
      node.toString
  }
}