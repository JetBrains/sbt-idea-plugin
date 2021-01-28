package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.runIdea.{IntellijVMOptions, JRE}
import sbt._

trait Quirks { this: Keys.type =>

  val toolsJar: File = file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"

  val pluginsWithScala = Seq( // TODO: add more
    "org.intellij.scala",
    "org.jetbrains.plugins.hocon",
    "intellij.haskell"
  )

  final val newClassloadingSinceVersion = "203.5251"

  def makeScalaLibraryProvided(libs: Seq[ModuleID]): Seq[ModuleID] = libs.map {
    case id if id.name.contains("scala-library") => id % "provided"
    case other => other
  }

  def filterScalaLibrary(mappings: Seq[(sbt.ModuleID, Option[String])]): Seq[(sbt.ModuleID, Option[String])] =
    mappings.filterNot {
      case (module, _) if module.name.contains("scala-library") => true
      case _ => false
    }

  def hasPluginsWithScala(plugins: Seq[IntellijPlugin]): Boolean =
    plugins.exists(plugin => pluginsWithScala.exists(id => plugin.toString.matches(s".*$id.*")))

  def java9PlusOptions(intellijVMOptions: IntellijVMOptions)(implicit jre: JRE): IntellijVMOptions =
    if(jre.version > 9)
      intellijVMOptions.copy(gc = "")
    else
      intellijVMOptions

}
