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

  final case class DepsOnly(targetPath: String = "") extends PackagingMethod

  final case class MergeIntoOther(project: Project) extends PackagingMethod
  /**
   * Package this project as a standalone artifact.
   *
   * @param targetPath path of the generated jar inside the plugin artifact,<br>
   *                   or an empty string to use `lib/<project-name>.jar`
   * @param static     if `true`, keep this project packaged as a jar when running `packageArtifactDynamic`;<br>
   *                   if `false`, dynamic packaging may expand project classes into the plugin `classes/` directory
   *                   to support source-level debugging. <br.
   *                   Regular `packageArtifact` and `packageArtifactZip` still produce jars for standalone projects.
   */
  final case class Standalone(targetPath: String = "", static: Boolean = false) extends PackagingMethod

  final case class PluginModule(moduleName: String, static: Boolean = false) extends PackagingMethod
}
