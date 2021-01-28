package org.jetbrains.sbtidea.tasks

import java.nio.file.Path

import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.api.InstallContext
import org.jetbrains.sbtidea.download.plugin._
import sbt.Keys._
import sbt._

object CreatePluginsClasspath {

  def collectPluginRoots(ideaBaseDir: Path, ideaBuildInfo: BuildInfo, plugins: Seq[IntellijPlugin], log: PluginLogger, moduleNameHint: String = ""): Seq[Path] = {
    implicit val context: InstallContext = InstallContext(baseDirectory = ideaBaseDir, downloadDirectory = ideaBaseDir)
    implicit val remoteRepoApi: PluginRepoUtils = new PluginRepoUtils
    implicit val localRegistry: LocalPluginRegistry = new LocalPluginRegistry(ideaBaseDir)

    PluginLogger.bind(log)

    val resolved = plugins.map(pl => PluginDependency(pl, ideaBuildInfo, Seq.empty).resolve)
    val allDependencies = resolved.flatten
    val duplicates = resolved
      .filter(_.nonEmpty)
      .map { chain => chain.head -> resolved.filter(_.tail.contains(chain.head)).map(_.head) }
      .groupBy(_._1)
      .map { case (k, v) => k -> v.flatMap { case (_, j) => j } }
      .filter(_._2.nonEmpty)

    duplicates.collect { case (LocalPlugin(_, PluginDescriptor(id, _, _, _, _, _, _), _), parents) =>
      val thisNonOptionalDependency = PluginDescriptor.Dependency(id, optional = false)
      val parentIds = parents.collect {
        case LocalPlugin(_, PluginDescriptor(parentId, _, _, _, _, _, deps), _) if deps.contains(thisNonOptionalDependency) => parentId
      }
      if (parentIds.nonEmpty)
        log.warn(s"Plugin [$id] is already included by: [${parentIds.mkString(", ")}]${if (moduleNameHint.nonEmpty) s" in project '$moduleNameHint'" else ""}")
    }
    val roots = allDependencies.collect { case LocalPlugin(_, descriptor, root) => root }.distinct
    roots
  }

  def apply(ideaBaseDir: Path, ideaBuildInfo: BuildInfo, plugins: Seq[IntellijPlugin], log: PluginLogger, moduleNameHint: String = ""): Classpath = {
    val roots = collectPluginRoots(ideaBaseDir, ideaBuildInfo, plugins, log, moduleNameHint)
    val pluginsFinder = roots
      .foldLeft(PathFinder.empty) { (pathFinder, pluginRoot) =>
        if (pluginRoot.toFile.isDirectory)
          pathFinder +++ ((pluginRoot.toFile / "lib") * (globFilter("*.jar") -- "asm*.jar"))
        else
          pathFinder +++ pluginRoot.toFile
      }
    pluginsFinder.classpath
  }

}
