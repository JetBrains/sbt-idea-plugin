package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.*
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.api.InstallContext
import org.jetbrains.sbtidea.download.plugin.*
import sbt.*
import sbt.Keys.*

import java.nio.file.Path

object CreatePluginsClasspath {

  def collectPluginRoots(ideaBaseDir: Path, ideaBuildInfo: BuildInfo, plugins: Seq[IntellijPlugin], log: PluginLogger, moduleNameHint: String = ""): Seq[(PluginDescriptor, Path)] = {
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
    val roots = allDependencies.collect { case LocalPlugin(_, descriptor, root) => descriptor -> root }.distinct
    roots
  }

  def buildPluginClassPaths(ideaBaseDir: Path, ideaBuildInfo: BuildInfo, plugins: Seq[IntellijPlugin], log: PluginLogger, addSources: Boolean, moduleNameHint: String = ""): Seq[(PluginDescriptor, Classpath)] = {
    val roots = collectPluginRoots(ideaBaseDir, ideaBuildInfo, plugins, log, moduleNameHint)
    roots.map { case (descriptor, pluginRoot) =>
      val pluginsFinder =
        if (pluginRoot.toFile.isDirectory)
          PathFinder.empty +++ pluginRoot.toFile / "lib" * (globFilter("*.jar") -- "asm*.jar")
        else
          PathFinder.empty +++ pluginRoot.toFile

      val pluginModule =
        if (addSources) descriptor.vendor % descriptor.id % descriptor.version withSources()
        else            descriptor.vendor % descriptor.id % descriptor.version

      val pluginArtifact  = Artifact(s"IJ-PLUGIN[${descriptor.id}]", "IJ-PLUGIN")

      val pluginAttrs: AttributeMap = AttributeMap.empty
        .put(artifact.key, pluginArtifact)
        .put(moduleID.key, pluginModule)
        .put(configuration.key, Compile)

      descriptor -> pluginsFinder.classpath.map(f => Attributed(f.data)(pluginAttrs))
    }
  }

  def apply(ideaBaseDir: Path, ideaBuildInfo: BuildInfo, plugins: Seq[IntellijPlugin], log: PluginLogger, addSources: Boolean, moduleNameHint: String = ""): Classpath =
    buildPluginClassPaths(ideaBaseDir, ideaBuildInfo, plugins, log, addSources, moduleNameHint).flatMap(_._2)

}
