package org.jetbrains.sbtidea.packaging.structure

/**
  * Internal, sbt-agnostic packaging model used by extracted project structure.
  *
  * See [[org.jetbrains.sbtidea.packaging.PackagingMethod]] for the rationale and the difference
  * between sbt-facing and structure-facing representations.
  */
sealed trait PackagingMethod

object PackagingMethod {
  final case class Skip() extends PackagingMethod

  /** See docs of [[org.jetbrains.sbtidea.packaging.PackagingMethod.MergeIntoParent]] */
  final case class MergeIntoParent() extends PackagingMethod

  final case class DepsOnly(targetPath: String) extends PackagingMethod

  final case class MergeIntoOther(project: PackagedProjectNode) extends PackagingMethod

  /** See docs of [[org.jetbrains.sbtidea.packaging.PackagingMethod.Standalone]] */
  final case class Standalone(targetPath: String, static: Boolean) extends PackagingMethod

  final case class PluginModule(moduleName: String, static: Boolean) extends PackagingMethod
}
