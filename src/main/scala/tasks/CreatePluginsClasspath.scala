package com.dancingrobot84.sbtidea
package tasks

import sbt._
import Keys._


object CreatePluginsClasspath {
  def apply(pluginsBase: File, pluginsUsed: Seq[String]): Classpath = {
    val pluginsDirs = pluginsUsed.foldLeft(PathFinder.empty) { (paths, plugin) =>
      paths +++ (pluginsBase / plugin) +++ (pluginsBase / plugin / "lib")
    }
    (pluginsDirs * (globFilter("*.jar") -- "asm*.jar")).classpath
  }
}
