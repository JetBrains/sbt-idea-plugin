package org.jetbrains.sbtidea.structure.sbtImpl

import java.io.File

import sbt.Def.Classpath
import sbt.{ModuleID, ProjectRef, UpdateReport}

trait CommonSbtProjectData {
  def thisProject: ProjectRef
  def name: String
  def cp: Classpath
  def definedDeps: Seq[ModuleID]
  def productDirs: Seq[File]
  def report: UpdateReport
}
