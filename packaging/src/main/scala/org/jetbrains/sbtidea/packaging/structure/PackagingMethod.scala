package org.jetbrains.sbtidea.packaging.structure

sealed trait PackagingMethod

object PackagingMethod {
  final case class Skip() extends PackagingMethod
  final case class MergeIntoParent() extends PackagingMethod
  final case class DepsOnly(targetPath: String) extends PackagingMethod
  final case class MergeIntoOther(project: PackagedProjectNode) extends PackagingMethod
  final case class Standalone(targetPath: String, static: Boolean) extends PackagingMethod
  final case class PluginModule(moduleName: String, static: Boolean) extends PackagingMethod
}