package org.jetbrains.sbtidea.tasks

import sbt._
import Keys._
import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.LocalPluginRegistry


object CreatePluginsClasspath {
  def apply(pluginsBase: File, pluginsUsed: Seq[String], externalPlugins: Seq[IdeaPlugin], log: PluginLogger): Classpath = {
    val localRegistry = new LocalPluginRegistry(pluginsBase.getParentFile.toPath, log)
    val externalPluginsFinder = externalPlugins.map(localRegistry.getInstalledPluginRoot).map(_.toFile).foldLeft(PathFinder.empty) {
      (pathFinder, pluginRoot) => pathFinder +++ pluginRoot +++ (pluginRoot / "lib")
    }
    val bundledPluginsFinder = pluginsUsed.foldLeft(PathFinder.empty) { (paths, plugin) =>
      paths +++ (pluginsBase / plugin) +++ (pluginsBase / plugin / "lib")
    }
    val pluginsFinder = bundledPluginsFinder +++ externalPluginsFinder
    (pluginsFinder * (globFilter("*.jar") -- "asm*.jar")).classpath
  }
}
