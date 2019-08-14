package org.jetbrains.sbtidea.tasks.packaging.structure

import scala.language.implicitConversions

import org.jetbrains.sbtidea.tasks.packaging.ProjectScalaVersion
import sbt._

package object sbtImpl {
  import org.jetbrains.sbtidea.Keys

  implicit class ModuleIdExt(val moduleId: ModuleID) extends AnyVal {

    def key(implicit scalaVersion: ProjectScalaVersion): ModuleKey = {
      val versionSuffix = moduleId.crossVersion match {
        case _:CrossVersion.Binary if scalaVersion.isDefined =>
          "_" + CrossVersion.binaryScalaVersion(scalaVersion.str)
        case _ => ""
      }

      ModuleKeyImpl(
        moduleId.organization %  (moduleId.name + versionSuffix) % moduleId.revision,
        moduleId.extraAttributes
          .map    { case (k, v) => k.stripPrefix("e:") -> v }
          .filter { case (k, _) => k == "scalaVersion" || k == "sbtVersion" })
    }

  }

  trait Ref2Node {
    def findProjectRef(project: sbt.Project): Option[sbt.ProjectRef]
    def getNode(ref: sbt.ProjectRef): ProjectNode
  }

  implicit def keys2Structure(p: Keys.PackagingMethod)(implicit ref2Node: Ref2Node): PackagingMethod = p match {
    case Keys.PackagingMethod.Skip() =>
      PackagingMethod.Skip()
    case Keys.PackagingMethod.MergeIntoParent() =>
      PackagingMethod.MergeIntoParent()
    case Keys.PackagingMethod.DepsOnly(targetPath) =>
      PackagingMethod.DepsOnly(targetPath)
    case Keys.PackagingMethod.Standalone(targetPath, static) =>
      PackagingMethod.Standalone(targetPath, static)
    case Keys.PackagingMethod.MergeIntoOther(project) =>
      PackagingMethod.MergeIntoOther(ref2Node.findProjectRef(project).map(ref2Node.getNode).getOrElse(???))
  }

  class SbtProjectExtractException(message: String) extends Exception(message)

  private[sbtImpl] def extractProjectName(project: ProjectReference): String = {
    val str = project.toString
    val commaIdx = str.indexOf(',')
    str.substring(commaIdx+1, str.length-1)
  }
}
