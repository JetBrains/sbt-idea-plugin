package org.jetbrains.sbt
package tasks

import org.jetbrains.sbtidea.Keys.PackagingMethod
import sbt._
import sbt.jetbrains.apiAdapter._

object PluginPackager {

  case class Node(me: ProjectRef, method: PackagingMethod, libs: Seq[(ModuleID, Option[File])], deps: Seq[Node])

  def apply(rootProject: ProjectRef,
            projectsData: Seq[(ProjectRef, Seq[ModuleID], Seq[(ModuleID, Option[File])], PackagingMethod)],
            buildDependencies: BuildDependencies): File = {

    val projectMap = projectsData.iterator.map( x => x._1 -> x).toMap

    def process(ref: ProjectRef): Node = {
        val (_, libs, mappings, method) = projectMap(ref)
        Node(ref, method, libs.map(_ -> None), buildDependencies.classpathRefs(ref) map process)
    }

    println(process(rootProject))
    new File(".")
  }
}
