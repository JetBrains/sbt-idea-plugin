package org.jetbrains.sbtidea

import sbt.Keys.{Classpath, moduleID}
import sbt._

trait Quirks { this: Keys.type =>

  private val pluginsWithScala = Seq( // TODO: add more
    "org.intellij.scala",
    "org.jetbrains.plugins.hocon",
    "intellij.haskell"
  )

  def makeScalaLibraryProvided(libs: Seq[ModuleID]): Seq[ModuleID] = libs.map {
    case id if id.name.contains("scala-library") => id % "provided"
    case other => other
  }

  def filterScalaLibrary(mappings: Seq[(sbt.ModuleID, Option[String])]): Seq[(sbt.ModuleID, Option[String])] =
    mappings.filterNot {
      case (module, _) if module.name.contains("scala-library") => true
      case _ => false
    }

  def filterScalaLibraryCp(cp: Classpath): Classpath =
    cp.filterNot(_.get(moduleID.key).exists(_.name.contains("scala-library")))

  def hasPluginsWithScala(plugins: Seq[IntellijPlugin]): Boolean =
    plugins.exists(plugin => pluginsWithScala.exists(id => plugin.toString.matches(s".*$id.*")))
}
