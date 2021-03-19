package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.ExcludeFilter.ExcludeFilter

import java.nio.file.Path
import sbt._

import java.io.File

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

object MAPPING_KIND extends Enumeration {
  type MAPPING_KIND = Value
  val TARGET, LIB, LIB_ASSEMBLY, MISC, UNDEFINED = Value
}

case class MappingMetaData(shading: Seq[ShadePattern], excludeFilter: ExcludeFilter, static: Boolean, project: Option[String], kind: MAPPING_KIND.MAPPING_KIND)
object     MappingMetaData { val EMPTY: MappingMetaData = MappingMetaData(Seq.empty, ExcludeFilter.AllPass, static = true, project = None, kind = MAPPING_KIND.UNDEFINED) }

case class Mapping(from: File, to: File, metaData: MappingMetaData)


class SkipEntryException extends Exception
