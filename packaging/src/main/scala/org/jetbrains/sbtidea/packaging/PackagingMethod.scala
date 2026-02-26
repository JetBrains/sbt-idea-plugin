package org.jetbrains.sbtidea.packaging

import sbt.Project

/**
  * User-facing, sbt-bound packaging model used in sbt settings (for example, `packageMethod := ...`).
  *
  * This type intentionally depends on sbt API (`MergeIntoOther` stores an `sbt.Project`) and keeps
  * user-friendly defaults in constructors (`DepsOnly()` and `Standalone()`), because it is part of the
  * sbt configuration DSL.
  *
  * During extraction this value is converted into the sbt-agnostic
  * [[org.jetbrains.sbtidea.packaging.structure.PackagingMethod]] by
  * [[org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackagingStructureExtractor.keys2Structure]].
  *
  * History:
  *  - 2019-08: split into two models (sbt-facing + structure-facing) during structure extraction refactoring.
  *  - 2021-03: this sbt-facing type was extracted from `PackagingDefs` into its own file.
  */
sealed trait PackagingMethod

object PackagingMethod {
  final case class Skip() extends PackagingMethod
  final case class MergeIntoParent() extends PackagingMethod
  final case class DepsOnly(targetPath: String = "") extends PackagingMethod
  final case class MergeIntoOther(project: Project) extends PackagingMethod
  final case class Standalone(targetPath: String = "", static: Boolean = false) extends PackagingMethod
  final case class PluginModule(moduleName: String, static: Boolean = false) extends PackagingMethod
}
