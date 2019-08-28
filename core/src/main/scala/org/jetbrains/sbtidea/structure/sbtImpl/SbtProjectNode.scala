package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.structure.ProjectNode
import sbt._

import scala.collection.mutable

trait SbtProjectNode extends ProjectNode {
  def ref: ProjectRef
  def cache(implicit projectCache: mutable.Map[ProjectRef, ProjectNode]): SbtProjectNode = { projectCache += ref -> this; this }
  def name:  String = extractProjectName(ref)

  override def toString: String = s"PN($name)"
  override def hashCode(): Int = ref.hashCode()
  override def equals(obj: Any): Boolean = obj match {
    case x:SbtProjectNode => ref.equals(x.ref)
    case _ => false
  }
}