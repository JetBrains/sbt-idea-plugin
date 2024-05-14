package org.jetbrains.sbtidea.structure

import sbt.*

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

  private def isScala2Library(lib: ModuleID): Boolean = lib.name == "scala-library"
  private def isScala3Library(lib: ModuleID): Boolean = lib.name == "scala3-library_3"
  def isScalaLibrary(lib: ModuleID): Boolean = isScala2Library(lib) || isScala3Library(lib)

  /**
   * @return Scala 3 library if it's present<br>
    *         orScala 2 library if its present<br>
    *         or None otherwise
    */
  def detectMainScalaLibrary(dependencies: Seq[ModuleID]): Option[ModuleID] = {
    val scalaLibraries = dependencies.filter(isScalaLibrary)
    if (scalaLibraries.isEmpty) None else Some(scalaLibraries.maxBy(isScala3Library))
  }

  def detectScalaVersion(projectData: CommonSbtProjectData): ProjectScalaVersionImpl = {
    val mainScalaLibraryFromClasspath = detectMainScalaLibrary(projectData.cp.flatMap(_.metadata.get(sbt.Keys.moduleID.key)))

    val mainScalaLibrary = if (mainScalaLibraryFromClasspath.nonEmpty)
      mainScalaLibraryFromClasspath
    else
      detectMainScalaLibrary(projectData.definedDeps)

    ProjectScalaVersionImpl(mainScalaLibrary)
  }
}
