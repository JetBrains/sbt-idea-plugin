package org.jetbrains.sbtidea.download.plugin
import org.jetbrains.sbtidea.*
import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea.download.FileDownloader
import org.jetbrains.sbtidea.download.api.*

import java.io
import java.net.URL
import java.nio.file.Files

class PluginResolver(
  private val processedPlugins: Set[IntellijPlugin] = Set.empty,
  private val resolveSettings: IntellijPlugin.Settings
)(implicit ctx: InstallContext, repo: PluginRepoApi, localRegistry: LocalPluginRegistryApi) extends Resolver[PluginDependency] {

  // modules that are inside idea.jar
  private val INTERNAL_MODULE_PREFIX = "com.intellij.modules."

  override def resolve(pluginDependency: PluginDependency): Seq[PluginArtifact] = {
    if(processedPlugins.contains(pluginDependency.plugin)) {
      PluginLogger.warn(s"Circular plugin dependency detected: $pluginDependency already processed")
      Seq.empty
    } else {
      pluginDependency.plugin match {
        case key: IntellijPlugin.Url =>
          resolvePluginByUrl(pluginDependency)(key)
        case key: IntellijPlugin.IdOwner =>
          resolvePluginById(pluginDependency)(key)
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

  private def resolvePluginByUrl(plugin: PluginDependency)(key: IntellijPlugin.Url): Seq[PluginArtifact] =
    RemotePluginArtifact(plugin, key.url) :: Nil

  private def resolvePluginById(plugin: PluginDependency)(key: IntellijPlugin.IdOwner): Seq[PluginArtifact] = {
    val alreadyInstalled = localRegistry.isPluginInstalled(key)

    val descriptor: Either[io.Serializable, PluginDescriptor] =
      if (alreadyInstalled)
        localRegistry.getPluginDescriptor(key)
      else
        key match {
          case IntellijPlugin.Id(id, version, channel) =>
            repo.getRemotePluginXmlDescriptor(plugin.buildInfo, id, channel)
          case IntellijPlugin.IdWithCustomUrl(id, version, downloadUrl) =>
            downloadAndExtractDescriptor(downloadUrl)
        }

    descriptor match {
      case Right(descriptor) =>
        val thisPluginArtifact: PluginArtifact =
          if (alreadyInstalled)
            LocalPlugin(plugin, descriptor, localRegistry.getInstalledPluginRoot(key))
          else
            RemotePluginArtifact(plugin, getPluginDownloadUrl(plugin, key))
        val pluginTransitiveDependencies = if (!resolveSettings.transitive) Nil else resolveDependencies(plugin, key, descriptor)
        thisPluginArtifact +: pluginTransitiveDependencies
      case Left(error) =>
        PluginLogger.error(s"Failed to resolve $plugin: $error"); Seq.empty
    }
  }

  private def getPluginDownloadUrl(plugin: PluginDependency, key: IntellijPlugin.IdOwner): URL =
    key match {
      case key: IntellijPlugin.Id =>
        repo.getPluginDownloadURL(plugin.buildInfo, key)
      case IntellijPlugin.IdWithCustomUrl(id, version, downloadUrl) =>
        downloadUrl
    }

  private def downloadAndExtractDescriptor(downloadUrl: URL)(implicit ctx: InstallContext): Either[String, PluginDescriptor] = {
    val downloadedFile = FileDownloader(ctx.downloadDirectory).download(downloadUrl)

    val extractDir = Files.createTempDirectory(ctx.downloadDirectory, s"tmp-resolve-downloads")
    sbt.IO.unzip(downloadedFile.toFile, extractDir.toFile)
    assert(Files.list(extractDir).count() == 1, s"Expected only single plugin folder in extracted archive, got: ${extractDir.toFile.list().mkString}")

    val tmpPluginDir = Files.list(extractDir).findFirst().get()
    val pluginDescriptor = LocalPluginRegistry.extractInstalledPluginDescriptorFileContent(tmpPluginDir)
    pluginDescriptor.right.map(_.content).map(PluginDescriptor.load)
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