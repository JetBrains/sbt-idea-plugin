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
    val plugin = pluginDependency.plugin

    val result = if (processedPlugins.contains(plugin)) {
      PluginLogger.warn(s"Circular plugin dependency detected: $pluginDependency already processed")
      Seq.empty
    }
    else if (localRegistry.isPluginInstalled(plugin))
      resolveInstalledPluginPlugin(pluginDependency, plugin)
    else plugin match {
      case key: IntellijPlugin.Url =>
        resolvePluginByUrl(pluginDependency)(key)
      case key: IntellijPlugin.IdOwner =>
        resolvePluginById(pluginDependency)(key)
      case key: IntellijPlugin.BundledFolder =>
        PluginLogger.error(s"Cannot find bundled plugin root for folder name: ${key.name}")
        Seq.empty
    }

    result.distinct
  }

  private def resolveDependencies(plugin: PluginDependency, key: IntellijPlugin, descriptor: PluginDescriptor): Seq[PluginArtifact] = {
    if (!resolveSettings.transitive)
      return Seq.empty

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
    val descriptor: Either[io.Serializable, PluginDescriptor] = key match {
      case IntellijPlugin.Id(id, version, channel) =>
        repo.getRemotePluginXmlDescriptor(plugin.buildInfo, id, channel)
      case IntellijPlugin.IdWithCustomUrl(_, _, downloadUrl) =>
        downloadAndExtractDescriptor(downloadUrl)
    }

    descriptor match {
      case Right(descriptor) =>
        val downloadUrl = getPluginDownloadUrl(plugin, key)
        val thisPluginArtifact = RemotePluginArtifact(plugin, downloadUrl)
        val resolvedDeps = resolveDependencies(plugin, key, descriptor)
        thisPluginArtifact +: resolvedDeps
      case Left(error) =>
        PluginLogger.error(s"Failed to resolve $plugin: $error")
        Seq.empty
    }
  }

  private def getPluginDownloadUrl(plugin: PluginDependency, key: IntellijPlugin.IdOwner): URL = key match {
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

  private def resolveInstalledPluginPlugin(plugin: PluginDependency, key: IntellijPlugin): Seq[PluginArtifact] = {
    val descriptor = localRegistry.getPluginDescriptor(key)
    descriptor match {
      case Right(descriptor) =>
        val root = localRegistry.getInstalledPluginRoot(key)
        val thisPluginArtifact = LocalPlugin(plugin, descriptor, root)
        val resolvedDeps = resolveDependencies(plugin, key, descriptor)
        thisPluginArtifact +: resolvedDeps
      case Left(error) =>
        PluginLogger.error(s"Cannot extract bundled plugin descriptor from $plugin: $error")
        Seq.empty
    }
  }
}