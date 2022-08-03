package org.jetbrains.sbtidea.download.plugin
import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea._
import org.jetbrains.sbtidea.download.FileDownloader
import org.jetbrains.sbtidea.download.api._

import java.nio.file.Files


class PluginResolver(private val processedPlugins: Set[IntellijPlugin] = Set.empty, private val resolveSettings: IntellijPlugin.Settings)
                    (implicit ctx: InstallContext, repo: PluginRepoApi, localRegistry: LocalPluginRegistryApi) extends Resolver[PluginDependency] {

  // modules that are inside idea.jar
  private val INTERNAL_MODULE_PREFIX = "com.intellij.modules."

  override def resolve(pluginDependency: PluginDependency): Seq[PluginArtifact] = {
    if(processedPlugins.contains(pluginDependency.plugin)) {
      PluginLogger.warn(s"Circular plugin dependency detected: $pluginDependency already processed")
      Seq.empty
    } else {
      pluginDependency.plugin match {
        case key: IntellijPlugin.Url =>
          resolveUrlPlugin(pluginDependency)(key)
        case key: IntellijPlugin.Id =>
          resolvePluginId(pluginDependency)(key)
        case key: IntellijPlugin.BundledFolder =>
          resolveBundledPlugin(pluginDependency)(key)
      }
    }.distinct
  }

  private def resolveDependencies(plugin: PluginDependency, key: IntellijPlugin, descriptor: PluginDescriptor) = {
    descriptor.dependsOn
      .filterNot(!resolveSettings.optionalDeps && _.optional)              // skip all optional plugins if flag is set
      .filterNot(dep => resolveSettings.excludedIds.contains(dep.id))      // remove plugins specified by user blacklist
      .filterNot(_.id.startsWith(INTERNAL_MODULE_PREFIX))                  // skip plugins embedded in idea.jar
      .filterNot(dep => dep.optional && !localRegistry.isPluginInstalled(dep.id.toPlugin)) // skip optional non-bundled plugins
      .map(dep => PluginDependency(dep.id.toPlugin, plugin.buildInfo))
      .flatMap(new PluginResolver(processedPlugins = processedPlugins + key, resolveSettings).resolve)
  }

  private def resolveUrlPlugin(plugin: PluginDependency)(key: IntellijPlugin.Url): Seq[PluginArtifact] =
    RemotePluginArtifact(plugin, key.url) :: Nil

  private def resolvePluginId(plugin: PluginDependency)(key: IntellijPlugin.Id): Seq[PluginArtifact] = {
    val alreadyInstalled = localRegistry.isPluginInstalled(key)
    val descriptor =
      if (alreadyInstalled)
        localRegistry.getPluginDescriptor(key)
      else if (key.url.isDefined) {
        val downloadedFile = FileDownloader(ctx.downloadDirectory).download(key.url.get)
        val extractDir = Files.createTempDirectory(ctx.downloadDirectory, s"tmp-resolve-downloads")
        sbt.IO.unzip(downloadedFile.toFile, extractDir.toFile)
        assert(Files.list(extractDir).count() == 1, s"Expected only single plugin folder in extracted archive, got: ${extractDir.toFile.list().mkString}")
        val tmpPluginDir = Files.list(extractDir).findFirst().get()
        LocalPluginRegistry.extractInstalledPluginDescriptor(tmpPluginDir).right.map(PluginDescriptor.load)
      } else repo.getRemotePluginXmlDescriptor(plugin.buildInfo, key.id, key.channel)

    descriptor match {
      case Right(descriptor) =>

        val thisPluginArtifact =
          if (alreadyInstalled)
            LocalPlugin(plugin, descriptor, localRegistry.getInstalledPluginRoot(key))
          else {
            val url = key.url.getOrElse(repo.getPluginDownloadURL(plugin.buildInfo, key))
            RemotePluginArtifact(plugin, url)
          }

        val resolvedDeps =
          if (resolveSettings.transitive)
            resolveDependencies(plugin, key, descriptor)
          else
            Seq.empty

        thisPluginArtifact +: resolvedDeps
      case Left(error) => PluginLogger.error(s"Failed to resolve $plugin: $error"); Seq.empty
    }
  }

  private def resolveBundledPlugin(plugin: PluginDependency)(key: IntellijPlugin.BundledFolder): Seq[PluginArtifact] = {
    if (localRegistry.isPluginInstalled(key)) {
      val root = localRegistry.getInstalledPluginRoot(key)
      val descriptor = localRegistry.getPluginDescriptor(key)
      descriptor match {
        case Right(value) =>
          val thisPluginArtifact = LocalPlugin(plugin, value, root)
          val resolvedDeps =
            if (resolveSettings.transitive)
              resolveDependencies(plugin, key, value)
            else
              Seq.empty
          thisPluginArtifact +: resolvedDeps
        case Left(error) =>
          PluginLogger.error(s"Cannot extract bundled plugin descriptor from $plugin: $error")
          Seq.empty
      }
    } else {
      PluginLogger.error(s"Cannot find bundled plugin root for folder name: ${key.name}")
      Seq.empty
    }
  }
}