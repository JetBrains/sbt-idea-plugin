package org.jetbrains.sbtidea

import sbt.Keys.{Classpath, moduleID}
import sbt.{Keys as SbtKeys, *}

trait Quirks { this: Keys.type =>

  private val pluginsWithScala = Seq( // TODO: add more
    "org.intellij.scala",
    "org.jetbrains.plugins.hocon",
    "intellij.haskell"
  )

  def makeScalaLibraryProvided(libs: Seq[ModuleID]): Seq[ModuleID] = libs.map {
    case id if isScalaLibrary(id) => id % "provided"
    case other => other
  }

  def filterScalaLibrary(mappings: Seq[(sbt.ModuleID, Option[String])]): Seq[(sbt.ModuleID, Option[String])] =
    mappings.filterNot {
      case (module, _) if isScalaLibrary(module) => true
      case _ => false
    }

  def filterScalaLibraryCp(cp: Classpath): Classpath =
    cp.filterNot(_.get(moduleID.key).exists(isScalaLibrary))

  def hasPluginsWithScala(plugins: Seq[IntellijPlugin]): Boolean =
    plugins.exists(plugin => pluginsWithScala.exists(id => plugin.toString.matches(s".*$id.*")))

  private def isScalaLibrary(moduleId: ModuleID): Boolean = moduleId.name match {
    case "scala-library" | "scala3-library_3" => true
    case _ => false
  }
}
