package org.jetbrains.sbtidea.tasks.packaging

import sbt.ModuleID

case class ProjectScalaVersion(libModule: Option[ModuleID]) {
  def isDefined: Boolean = libModule.isDefined
  def str: String = libModule.map(_.revision).getOrElse("")
}
