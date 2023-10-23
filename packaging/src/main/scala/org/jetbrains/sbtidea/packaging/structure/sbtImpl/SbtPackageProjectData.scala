package org.jetbrains.sbtidea.packaging.structure.sbtImpl

import org.jetbrains.sbtidea.packaging.{ExcludeFilter, PackagingMethod, ShadePattern}
import org.jetbrains.sbtidea.structure.sbtImpl.CommonSbtProjectData
import sbt.*
import sbt.Def.Classpath

import java.io.File
import scala.language.implicitConversions

case class SbtPackageProjectData(thisProject: ProjectRef,
                                 thisProjectName: String,
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
