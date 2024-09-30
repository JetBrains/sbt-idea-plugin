package org.jetbrains.sbtidea.packaging

import sbt.Project

sealed trait PackagingMethod

object PackagingMethod {
  final case class Skip() extends PackagingMethod
  final case class MergeIntoParent() extends PackagingMethod
  final case class DepsOnly(targetPath: String = "") extends PackagingMethod
  final case class MergeIntoOther(project: Project) extends PackagingMethod
  final case class Standalone(targetPath: String = "", static: Boolean = false) extends PackagingMethod
  final case class PluginModule(moduleName: String, static: Boolean = false) extends PackagingMethod
}