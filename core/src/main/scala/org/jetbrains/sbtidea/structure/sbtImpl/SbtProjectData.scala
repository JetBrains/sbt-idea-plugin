package org.jetbrains.sbtidea.structure.sbtImpl

import sbt.*
import sbt.Def.Classpath

import java.io.File
import scala.language.implicitConversions


case class SbtProjectData(thisProject: ProjectRef,
                          thisProjectName: String,
                          cp: Classpath,
                          definedDeps: Seq[ModuleID],
                          productDirs: Seq[File],
                          report: UpdateReport) extends CommonSbtProjectData