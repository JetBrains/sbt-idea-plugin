package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.structure.ProjectNode
import sbt.*

trait SbtProjectNode extends ProjectNode {
  def ref: ProjectRef
  def name:  String

  override def toString: String = s"{$name}"
  override def hashCode(): Int = ref.hashCode()
  override def equals(obj: Any): Boolean = obj match {
    case x:SbtProjectNode => ref.equals(x.ref)
    case _ => false
  }
}