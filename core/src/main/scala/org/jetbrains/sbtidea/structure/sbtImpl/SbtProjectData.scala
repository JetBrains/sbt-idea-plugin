package org.jetbrains.sbtidea.structure.sbtImpl

import java.io.File

import sbt.Def.Classpath
import sbt._

import scala.language.implicitConversions


case class SbtProjectData(thisProject: ProjectRef,
                          cp: Classpath,
                          definedDeps: Seq[ModuleID],
                          productDirs: Seq[File],
                          report: UpdateReport) extends CommonSbtProjectData