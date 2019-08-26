package org.jetbrains.sbtidea.structure.sbtImpl

import java.io.File

import org.jetbrains.sbtidea.Keys.ShadePattern
import org.jetbrains.sbtidea.packaging
import org.jetbrains.sbtidea.packaging.artifact.ExcludeFilter.ExcludeFilter
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
                                 additionalMappings: Seq[(File, String)],
                                 packageMethod: packaging.PackagingMethod,
                                 shadePatterns: Seq[ShadePattern],
                                 excludeFilter: ExcludeFilter) extends CommonSbtProjectData
