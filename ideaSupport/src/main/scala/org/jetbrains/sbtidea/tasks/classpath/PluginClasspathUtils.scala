package org.jetbrains.sbtidea.tasks.classpath

import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.api.InstallContext
import org.jetbrains.sbtidea.download.plugin.*
import org.jetbrains.sbtidea.{IntellijPlugin, PluginJars, PluginLogger}
import sbt.*

import java.io.File
import java.nio.file.Path

object PluginClasspathUtils {

  def getPluginClasspathPattern(pluginRoot: File): Seq[String] =
    if (pluginRoot.isDirectory)
      pluginJarLocations(pluginRoot).map(dir => s"$dir${File.separator}*")
    else
      Seq(pluginRoot.toString)

  private def collectPluginClasspathJars(pluginRoot: File): Seq[File] = {
    val finders = if (pluginRoot.isDirectory)
      pluginJarLocations(pluginRoot).map(_ * globFilter("*.jar"))
    else
      Seq(singleFileFinder(pluginRoot))

    finders.foldLeft(PathFinder.empty)(_ +++ _).classpath.map(_.data)
  }

  private def pluginJarLocations(pluginRoot: File): Seq[File] =
    Seq(
      pluginRoot / "lib",
      pluginRoot / "lib" / "modules"
    ).filter(_.isDirectory) //ensure it's a directory and exists

  def buildPluginJars(
    ideaBaseDir: Path,
    ideaBuildInfo: BuildInfo,
    plugins: Seq[IntellijPlugin],
    log: PluginLogger,
    moduleNameHint: String = ""
  ): Seq[PluginJars] = {
    val roots = collectPluginRoots(ideaBaseDir, ideaBuildInfo, plugins, log, moduleNameHint)
    roots.map { case (descriptor, pluginRoot) =>
      val jars = PluginClasspathUtils.collectPluginClasspathJars(pluginRoot.toFile)
      PluginJars(descriptor, pluginRoot.toFile, jars)
    }
  }

  def collectPluginRoots(
    ideaBaseDir: Path,
    ideaBuildInfo: BuildInfo,
    plugins: Seq[IntellijPlugin],
    log: PluginLogger,
    moduleNameHint: String = ""
  ): Seq[(PluginDescriptor, Path)] = {
    implicit val context: InstallContext = InstallContext(baseDirectory = ideaBaseDir, downloadDirectory = ideaBaseDir)
    implicit val remoteRepoApi: PluginRepoUtils = new PluginRepoUtils
    implicit val localRegistry: LocalPluginRegistry = new LocalPluginRegistry(context)

    PluginLogger.bind(log)

    val resolved = plugins.map(pl => PluginDependency(pl, ideaBuildInfo, Seq.empty).resolve)
    reportPluginDuplicates(resolved, moduleNameHint, log)

    val allDependencies = resolved.flatten
    val roots = allDependencies.collect { case LocalPlugin(_, descriptor, root) => descriptor -> root }.distinct
    roots
  }

  private def reportPluginDuplicates(
    resolved: Seq[Seq[PluginArtifact]],
    moduleNameHint: String,
    log: PluginLogger,
  ): Unit = {
    val duplicates = findPluginDuplicates(resolved)
    duplicates.collect { case (LocalPlugin(_, PluginDescriptor(id, _, _, _, _, _, _), _), parents) =>
      val thisNonOptionalDependency = PluginDescriptor.Dependency(id, optional = false)
      val parentIds = parents.collect {
        case LocalPlugin(_, PluginDescriptor(parentId, _, _, _, _, _, deps), _) if deps.contains(thisNonOptionalDependency) =>
          parentId
      }
      if (parentIds.nonEmpty) {
        log.warn(s"Plugin [$id] is already included by: [${parentIds.mkString(", ")}]${if (moduleNameHint.nonEmpty) s" in project '$moduleNameHint'" else ""}")
      }
    }
  }

  private def findPluginDuplicates(resolved: Seq[Seq[PluginArtifact]]): Map[PluginArtifact, Seq[PluginArtifact]] =
    resolved
      .filter(_.nonEmpty)
      .map { chain => chain.head -> resolved.filter(_.tail.contains(chain.head)).map(_.head) }
      .groupBy(_._1)
      .map { case (k, v) => k -> v.flatMap { case (_, j) => j } }
      .filter(_._2.nonEmpty)
}
