package org.jetbrains.sbtidea.packaging.structure

sealed trait PackagingMethod

object PackagingMethod {
  final case class Skip() extends PackagingMethod

  /**
    * Merge this module into the nearest eligible standalone parent in the project dependency graph.
    *
    * In practice this is often the default/fallback mode for non-root projects when users do not
    * explicitly configure `packageMethod`.
    *
    * Fallback is assigned in the sbt-layer settings:
    * [[org.jetbrains.sbtidea.packaging.PackagingKeysInit.projectSettings]]
    *
    * This structure-level value is produced from the sbt-layer value in
    * [[org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackagingStructureExtractor.keys2Structure]]
    */
  final case class MergeIntoParent() extends PackagingMethod
  final case class DepsOnly(targetPath: String) extends PackagingMethod
  final case class MergeIntoOther(project: PackagedProjectNode) extends PackagingMethod
  final case class Standalone(targetPath: String, static: Boolean) extends PackagingMethod
  final case class PluginModule(moduleName: String, static: Boolean) extends PackagingMethod
}
