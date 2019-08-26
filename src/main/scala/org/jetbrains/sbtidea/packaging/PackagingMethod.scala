package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.structure.ProjectNode

sealed trait PackagingMethod

object PackagingMethod {
  final case class Skip() extends PackagingMethod
  final case class MergeIntoParent() extends PackagingMethod
  final case class DepsOnly(targetPath: String = "") extends PackagingMethod
  final case class MergeIntoOther(project: PackagedProjectNode) extends PackagingMethod
  final case class Standalone(targetPath: String = "", static: Boolean = false) extends PackagingMethod
}