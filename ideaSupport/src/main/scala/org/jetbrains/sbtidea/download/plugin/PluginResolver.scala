package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea.download.FileDownloader
import org.jetbrains.sbtidea.download.api.*
import org.jetbrains.sbtidea.{IntellijPlugin, PluginLogger as log}

import java.net.URL

class PluginResolver(
  private val processedPlugins: Set[IntellijPlugin] = Set.empty,
  private val resolveSettings: IntellijPlugin.Settings
)(implicit ctx: InstallContext, repo: PluginRepoApi, localRegistry: LocalPluginRegistryApi) extends Resolver[PluginDependency] {

  // modules that are inside idea.jar
  private val INTERNAL_MODULE_PREFIX = "com.intellij.modules."

  override def resolve(pluginDependency: PluginDependency): Seq[PluginArtifact] = {
    val plugin = pluginDependency.plugin

    val pluginDescriptorAndArtifact: Either[String, (PluginDescriptor, PluginArtifact)] =
      if (processedPlugins.contains(plugin))
        Left(s"Circular plugin dependency detected: $pluginDependency already processed")
      else if (localRegistry.isPluginInstalled(plugin))
        resolveInstalledPluginPlugin(pluginDependency, plugin)
      else plugin match {
        case key: IntellijPlugin.Url =>
          Right((null, RemotePluginArtifact(pluginDependency, key.url)))
        case key: IntellijPlugin.WithKnownId =>
          resolvePluginById(pluginDependency ,key)
        case key: IntellijPlugin.BundledFolder =>
          Left(s"Cannot find bundled plugin root for folder name: ${key.name}")
      }

    pluginDescriptorAndArtifact match {
      case Right((descriptor, artifact)) =>
        val resolvedDeps =
          if (needToResolveTransitiveDependencies(plugin))
            resolveDependencies(pluginDependency, plugin, descriptor)
          else
            Seq.empty
        artifact +: resolvedDeps
      case Left(errorMessage) =>
        log.error(errorMessage)
        Seq.empty
    }
  }

  //There is no strong reason why we do not resolve plugin descriptor and transitive dependencies for IntellijPlugin.Url
  //It just worked so before and I didn't change it.
  //Theoretically we could do the same as for IntellijPlugin.IdWithCustomUrl (download plugin, read descriptor, resolve dependencies)
  //But it's not even clear what are the use cases for IntellijPlugin.Url.
  //Is it even used in any plugin?
  private def needToResolveTransitiveDependencies(plugin: IntellijPlugin): Boolean =
    resolveSettings.transitive && !plugin.isInstanceOf[IntellijPlugin.Url]

  private def resolveDependencies(plugin: PluginDependency, key: IntellijPlugin, descriptor: PluginDescriptor): Seq[PluginArtifact] =
    descriptor.dependsOn
      .filterNot(!resolveSettings.optionalDeps && _.optional)              // skip all optional plugins if flag is set
      .filterNot(dep => resolveSettings.excludedIds.contains(dep.id))      // remove plugins specified by user blacklist
      .filterNot(_.id.startsWith(INTERNAL_MODULE_PREFIX))                  // skip plugins embedded in idea.jar
      .filterNot(dep => dep.optional && !localRegistry.isPluginInstalled(dep.id.toPlugin)) // skip optional non-bundled plugins
      .map(dep => PluginDependency(dep.id.toPlugin, plugin.buildInfo))
      .flatMap(new PluginResolver(processedPlugins = processedPlugins + key, resolveSettings).resolve)

  private def resolvePluginById(plugin: PluginDependency, key: IntellijPlugin.WithKnownId): Either[String, (PluginDescriptor, PluginArtifact)] = {
    val descriptor: Either[String, PluginDescriptor] = key match {
      case IntellijPlugin.Id(id, _, channel) =>
        repo.getRemotePluginXmlDescriptor(plugin.buildInfo, id, channel).left.map(_.toString)
      case withCustomUrl: IntellijPlugin.IdWithCustomUrl =>
        downloadAndExtractDescriptor(withCustomUrl)
    }
    descriptor match {
      case Right(descriptor) =>
        val downloadUrl = getPluginDownloadUrl(plugin, key)
        val artifact = RemotePluginArtifact(plugin, downloadUrl)
        Right((descriptor, artifact))
      case Left(error) =>
        Left(s"Failed to resolve plugin $plugin: $error")
    }
  }

  private def getPluginDownloadUrl(plugin: PluginDependency, key: IntellijPlugin.WithKnownId): URL = key match {
    case key: IntellijPlugin.Id =>
      repo.getPluginDownloadURL(plugin.buildInfo, key)
    case IntellijPlugin.IdWithCustomUrl(_, _, downloadUrl) =>
      downloadUrl
  }

  private def downloadAndExtractDescriptor(plugin: IntellijPlugin.IdWithCustomUrl)(implicit ctx: InstallContext): Either[String, PluginDescriptor] = {
    val downloadedFile = FileDownloader(ctx.downloadDirectory).download(plugin.downloadUrl)
    val tmpPluginDir = RepoPluginInstaller.extractPluginToTemporaryDir(
      downloadedFile,
      plugin,
      s"tmp-${plugin.id}-plugin"
    )
    val pluginDescriptor = LocalPluginRegistry.extractInstalledPluginDescriptorFileContent(tmpPluginDir)
    pluginDescriptor.right.map(_.content).map(PluginDescriptor.load)
  }

  private def resolveInstalledPluginPlugin(plugin: PluginDependency, key: IntellijPlugin): Either[String, (PluginDescriptor, PluginArtifact)] = {
    val descriptor = localRegistry.getPluginDescriptor(key)
    descriptor match {
      case Right(descriptor) =>
        val root = localRegistry.getInstalledPluginRoot(key)
        val artifact = LocalPlugin(plugin, descriptor, root)
        Right((descriptor, artifact))
      case Left(error) =>
        Left(s"Cannot find installed plugin descriptor $plugin: $error")
    }
  }
}