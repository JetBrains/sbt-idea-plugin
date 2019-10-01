package org.jetbrains.sbtidea

import sbt._

trait Quirks { this: Keys.type =>

  val toolsJar: File = file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"

  val pluginsWithScala = Seq( // TODO: add more
    "org.intellij.scala",
    "org.jetbrains.plugins.hocon",
    "intellij.haskell"
  )

  def hasPluginsWithScala(plugins: Seq[IntellijPlugin]): Boolean =
    !plugins.exists(plugin => pluginsWithScala.exists(id => plugin.toString.matches(s".*$id.*")))

  //noinspection MapGetOrElseBoolean : scala 2.10 nas no Option.exists
  def maybeToolsJar: Seq[File] = {  // JDI requires tools.jar for JDK 8 and earlier
    if (sys.props("java.version").split("\\.").headOption.map(_.toInt < 9).getOrElse(false))
      Seq(toolsJar)
    else
      Seq.empty
  }

}
