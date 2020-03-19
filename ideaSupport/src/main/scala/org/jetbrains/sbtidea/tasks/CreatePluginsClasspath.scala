package org.jetbrains.sbtidea.tasks

import sbt._
import Keys._
import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.plugin.LocalPluginRegistry
import org.jetbrains.sbtidea.download.plugin.LocalPluginRegistry._

// TODO: use allPlugins key instead of externalPlugins
object CreatePluginsClasspath {

  def apply(ideaBaseDir: File, plugins: Seq[IntellijPlugin], log: PluginLogger): Classpath = {
    val localRegistry = LocalPluginRegistry.instanceFor(ideaBaseDir.toPath)
    val pluginsFinder = plugins
      .map(localRegistry.getInstalledPluginRoot)
      .map(_.toFile)
      .foldLeft(PathFinder.empty) { (pathFinder, pluginRoot) =>
        if (pluginRoot.isDirectory)
          pathFinder +++ ((pluginRoot / "lib") * (globFilter("*.jar") -- "asm*.jar"))
        else
          pathFinder +++ pluginRoot
      }
    pluginsFinder.classpath
  }

}
