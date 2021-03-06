package org.jetbrains.sbtidea.packaging.structure.sbtImpl

import java.io.File

import org.jetbrains.sbtidea.packaging.ExcludeFilter
import org.jetbrains.sbtidea.packaging.{PackagingMethod, ShadePattern}
import org.jetbrains.sbtidea.structure.sbtImpl.CommonSbtProjectData
import sbt.Def.Classpath
import sbt._

import scala.language.implicitConversions

case class SbtPackageProjectData(thisProject: ProjectRef,
                                 cp: Classpath,
                                 definedDeps: Seq[ModuleID],
                                 additionalProjects: Seq[Project],
                                 assembleLibraries: Boolean,
                                 productDirs: Seq[File],
                                 report: UpdateReport,
                                 libMapping: Seq[(ModuleID, Option[String])],
                                 libraryBaseDir: File,
                                 additionalMappings: Seq[(File, String)],
                                 packageMethod: PackagingMethod,
                                 shadePatterns: Seq[ShadePattern],
                                 excludeFilter: ExcludeFilter) extends CommonSbtProjectData
