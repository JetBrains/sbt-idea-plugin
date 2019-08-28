package org.jetbrains.sbtidea.packaging

import java.nio.file.Path

import sbt._

trait PackagingDefs {
  case class ShadePattern(from: String, to: String)

  sealed trait PackagingMethod

  object PackagingMethod {
    final case class Skip() extends PackagingMethod
    final case class MergeIntoParent() extends PackagingMethod
    final case class DepsOnly(targetPath: String = "") extends PackagingMethod
    final case class MergeIntoOther(project: Project) extends PackagingMethod
    final case class Standalone(targetPath: String = "", static: Boolean = false) extends PackagingMethod
  }

  object ExcludeFilter {
    type ExcludeFilter = Path=>Boolean

    val AllPass: ExcludeFilter = (_:Path) => false

    def merge(filters: Iterable[ExcludeFilter]): ExcludeFilter =
      path => filters.exists(f => f(path))

  }

}
