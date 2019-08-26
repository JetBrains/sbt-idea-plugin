package org.jetbrains.sbtidea.structure.sbtImpl

import java.io.File

import sbt.Def.Classpath
import sbt.{ModuleID, Project, ProjectRef, UpdateReport}

trait CommonSbtProjectData {
  def thisProject: ProjectRef
  def additionalProjects: Seq[Project]
  def cp: Classpath
  def definedDeps: Seq[ModuleID]
  def productDirs: Seq[File]
  def report: UpdateReport
}
