package org.jetbrains.sbtidea.tasks.packaging.structure

sealed trait PackagingMethod

object PackagingMethod {
  final case class Skip() extends PackagingMethod
  final case class MergeIntoParent() extends PackagingMethod
  final case class DepsOnly(targetPath: String) extends PackagingMethod
  final case class MergeIntoOther(project: ProjectNode) extends PackagingMethod
  final case class Standalone(targetPath: String = "", static: Boolean = false) extends PackagingMethod
}