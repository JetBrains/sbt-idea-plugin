package org.jetbrains.sbtidea.structure

import sbt._

import scala.language.implicitConversions

package object sbtImpl {

  case class ProjectScalaVersionImpl(libModule: Option[ModuleID]) extends ProjectScalaVersion {
    def isDefined: Boolean = libModule.isDefined
    def str: String = libModule.map(_.revision).getOrElse("")
  }

  implicit class ModuleIdExt(val moduleId: ModuleID) extends AnyVal {

    def versionSuffix(implicit scalaVersion: ProjectScalaVersion): String = moduleId.crossVersion match {
      case _:CrossVersion.Binary if scalaVersion.isDefined =>
        "_" + CrossVersion.binaryScalaVersion(scalaVersion.str)
      case _ => ""
    }

    def key(implicit scalaVersion: ProjectScalaVersion): ModuleKey = {
      ModuleKeyImpl(
        moduleId.organization %  (moduleId.name + versionSuffix) % moduleId.revision,
        moduleId.extraAttributes
          .map    { case (k, v) => k.stripPrefix("e:") -> v }
          .filter { case (k, _) => k == "scalaVersion" || k == "sbtVersion" })
    }
  }

  class SbtProjectExtractException(message: String) extends Exception(message)

  private[sbtImpl] def extractProjectId(project: ProjectReference): String = {
    val str = project.toString
    val commaIdx = str.indexOf(',')
    str.substring(commaIdx+1, str.length-1)
  }
}
