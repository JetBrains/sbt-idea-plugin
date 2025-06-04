package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea.download.FileDownloader
import org.jetbrains.sbtidea.download.api.*
import org.jetbrains.sbtidea.{IntellijPlugin, PluginLogger as log}

import java.net.URL
import scala.collection.mutable

class PluginResolver(
  private val processedPlugins: Set[IntellijPlugin] = Set.empty,
  private val resolveSettings: IntellijPlugin.Settings
)(implicit ctx: IdeInstallationProcessContext, repo: PluginRepoApi, localRegistry: LocalPluginRegistryApi) extends Resolver[PluginDependency] {

  //Keeping multiple errors in `Seq[String]` mainly for the case when IntellijPlugin.Id.fallbackDownloadUrl is not empty
  //In that case, we try to resolve the artifact twice and want to report both errors
  private type PluginDescriptorAndArtifactResolveResult = Either[Seq[String], (PluginDescriptor, PluginArtifact)]

  override def resolve(pluginDependency: PluginDependency): Seq[PluginArtifact] = {
    val plugin = pluginDependency.plugin

    val pluginDescriptorAndArtifact: PluginDescriptorAndArtifactResolveResult =
      if (processedPlugins.contains(plugin)) {
        log.warn(s"Circular plugin dependency detected: $pluginDependency already processed")
        Left(Nil)
      }
      else if (localRegistry.isPluginInstalled(plugin))
        resolveInstalledPluginPlugin(pluginDependency)
      else plugin match {
        case key: IntellijPlugin.WithKnownId =>
          resolvePluginById(pluginDependency, key)
        case key: IntellijPlugin.BundledFolder =>
          Left(Seq(s"Cannot find bundled plugin root for folder name: ${key.name}"))
      }

    pluginDescriptorAndArtifact match {
      case Right((descriptor, artifact)) =>
        val resolvedDeps =
          if (resolveSettings.transitive)
            resolveDependencies(pluginDependency, plugin, descriptor)
          else
            Seq.empty
        artifact +: resolvedDeps
      case Left(errorMessages) =>
        errorMessages.foreach(log.error(_))
        Seq.empty
    }
  }

  private def resolveDependencies(plugin: PluginDependency, key: IntellijPlugin, descriptor: PluginDescriptor): Seq[PluginArtifact] = {
    val productInfo = ctx.productInfo
    val coreModules = productInfo.modules ++ productInfo.productModulesNames
    val dependencies = descriptor.dependsOn
      .filterNot(!resolveSettings.optionalDeps && _.optional)                              // skip all optional plugins if flag is set
      .filterNot(dep => resolveSettings.excludedIds.contains(dep.id))                      // remove plugins specified by user blocklist
      .filterNot(dep => coreModules.contains(dep.id))                                       // skip dependencies on core product modules
      .filterNot(dep => dep.optional && !localRegistry.isPluginInstalled(dep.id.toPlugin)) // skip optional non-bundled plugins

    dependencies
      .map(dep => PluginDependency(dep.id.toPlugin, plugin.buildInfo))
      .flatMap(new PluginResolver(processedPlugins = processedPlugins + key, resolveSettings).resolve)
  }

  private def resolvePluginById(
    plugin: PluginDependency,
    key: IntellijPlugin.WithKnownId,
  ): PluginDescriptorAndArtifactResolveResult = {
    val resolved = resolvePluginByIdImpl(plugin, key)
    resolved.left.flatMap { originalErrors =>
      tryToResolveUsingFallbackUrlIfExists(plugin, key, originalErrors)
    }
  }

  private def tryToResolveUsingFallbackUrlIfExists(
    plugin: PluginDependency,
    key: IntellijPlugin.WithKnownId,
    originalErrors: Seq[String]
  ): PluginDescriptorAndArtifactResolveResult = {
    val fallbackDownloadUrl = key.fallbackDownloadUrl
    fallbackDownloadUrl match {
      case Some(fallbackUrl) =>
        log.warn(s"Failed to resolve plugin descriptor at marketplace\nTrying to resolve at a fallback url $fallbackUrl")
        val keyNew = IntellijPlugin.IdWithDownloadUrl(key.id, fallbackUrl)
        val pluginNew = plugin.copy(plugin = keyNew)
        val result = resolvePluginByIdImpl(pluginNew, keyNew)
        result.left.map(error => originalErrors ++ error)
      case None =>
        Left(originalErrors)
    }
  }

  private def resolvePluginByIdImpl(
    plugin: PluginDependency,
    key: IntellijPlugin.WithKnownId,
  ): PluginDescriptorAndArtifactResolveResult = {
    val descriptor: Either[String, PluginDescriptor] = key match {
      case IntellijPlugin.Id(id, _, channel, _) =>
        repo.getRemotePluginXmlDescriptor(plugin.buildInfo, id, channel).left.map(_.toString)
      case withCustomUrl: IntellijPlugin.IdWithDownloadUrl =>
        downloadAndExtractDescriptor(withCustomUrl)
    }
    descriptor match {
      case Right(descriptor) =>
        val downloadUrl = getPluginDownloadUrl(plugin, key)
        val artifact = RemotePluginArtifact(plugin, downloadUrl)
        Right((descriptor, artifact))
      case Left(error) =>
        Left(Seq(s"Failed to resolve $plugin: $error"))
    }
  }

  private def downloadAndExtractDescriptor(plugin: IntellijPlugin.IdWithDownloadUrl)(implicit ctx: IdeInstallationProcessContext): Either[String, PluginDescriptor] = {
    val downloadedFile = FileDownloader(ctx).download(plugin.downloadUrl)
    val tmpPluginDir = RepoPluginInstaller.extractPluginToTemporaryDir(
      downloadedFile,
      plugin,
      s"tmp-${plugin.id}-plugin"
    )
    val pluginDescriptor = LocalPluginRegistry.extractInstalledPluginDescriptorFileContent(tmpPluginDir)
    pluginDescriptor.right.map(_.content).map(PluginDescriptor.load)
  }

  private def resolveInstalledPluginPlugin(pluginDependency: PluginDependency): PluginDescriptorAndArtifactResolveResult = {
    val pluginKey = pluginDependency.plugin
    val descriptor = localRegistry.getPluginDescriptor(pluginKey)
    descriptor match {
      case Right(descriptor) =>
        pluginKey match {
          case withId: IntellijPlugin.WithKnownId if localRegistry.isDownloadedPlugin(withId) =>
            val downloadUrl = getPluginDownloadUrl(pluginDependency, withId)
            // reminder: remote plugins will be checked for actuality - it will check if there is a newer version at marketplace
            // (see org.jetbrains.sbtidea.download.plugin.RepoPluginInstaller.isInstalledPluginUpToDate)
            val artifact = RemotePluginArtifact(pluginDependency, downloadUrl)
            Right((descriptor, artifact))
          case _ =>
            val root = localRegistry.getInstalledPluginRoot(pluginKey)
            val artifact = LocalPlugin(pluginDependency, descriptor, root)
            Right((descriptor, artifact))
        }
      case Left(error) =>
        Left(Seq(s"Cannot find installed plugin descriptor $pluginDependency: $error"))
    }
  }

  private def getPluginDownloadUrl(plugin: PluginDependency, key: IntellijPlugin.WithKnownId): URL = key match {
    case key: IntellijPlugin.Id =>
      repo.getPluginDownloadURL(plugin.buildInfo, key)
    case IntellijPlugin.IdWithDownloadUrl(_, downloadUrl) =>
      downloadUrl
  }
}

object PluginResolver {
  /**
   * This marker set is needed to avoid accessing remote plugin metadata for the same plugin multiple times during import.
   *
   * The same plugin can be resolved multiple times during import as it can also be a dependency of other plugins or modules.
   * Each dependency is resolved independently.
   * But we can be sure that if the plugin artifact was not changed for the plugin, then we don't need to check it again.
   *
   * Yes, using a global state is not very nice, but it should be enough for our purposes
   */
  private val PluginsWithAlreadyCheckedFileNameOnRemote = mutable.Set.empty[IntellijPlugin.WithKnownId]
}
