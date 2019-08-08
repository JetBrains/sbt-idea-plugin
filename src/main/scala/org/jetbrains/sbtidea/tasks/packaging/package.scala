package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys.PackagingMethod
import org.jetbrains.sbtidea.tasks.packaging.artifact.ExcludeFilter.ExcludeFilter
import org.jetbrains.sbtidea.tasks.packaging.artifact.{ExcludeFilter, ShadePattern}
import sbt.Def.Classpath
import sbt._

package object packaging {

  case class ProjectScalaVersion(libModule: Option[ModuleID]) {
    def isDefined: Boolean = libModule.isDefined
    def str: String = libModule.map(_.revision).getOrElse("")
  }

  case class ProjectData(thisProject: ProjectRef,
                         cp: Classpath,
                         definedDeps: Seq[ModuleID],
                         additionalProjects: Seq[Project],
                         assembleLibraries: Boolean,
                         productDirs: Seq[File],
                         report: UpdateReport,
                         libMapping: Seq[(ModuleID, Option[String])],
                         additionalMappings: Seq[(File, String)],
                         packageMethod: PackagingMethod,
                         shadePatterns: Seq[ShadePattern],
                         excludeFilter: ExcludeFilter
                        )


  private[packaging] object MAPPING_KIND extends Enumeration {
    type MAPPING_KIND = Value
    val TARGET, LIB, LIB_ASSEMBLY, MISC, UNDEFINED = Value
  }

  private[packaging] case class MappingMetaData(shading: Seq[ShadePattern], excludeFilter: ExcludeFilter, static: Boolean, project: Option[String], kind: MAPPING_KIND.MAPPING_KIND)
  private[packaging] object     MappingMetaData { val EMPTY: MappingMetaData = MappingMetaData(Seq.empty, ExcludeFilter.AllPass, static = true, project = None, kind = MAPPING_KIND.UNDEFINED) }

  private[packaging] case class Mapping(from: File, to: File, metaData: MappingMetaData)

  type Mappings = Seq[Mapping]

  class SkipEntryException extends Exception

  implicit def MappingOrder[A <: Mapping]: Ordering[A] = Ordering.by(x => x.from -> x.to) // order by target jar file

}
