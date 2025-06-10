package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.download.api.*

import java.net.URL
import java.nio.file.Path

sealed trait PluginArtifact extends ResolvedArtifact {
  def caller: PluginDependency
}

final case class RemotePluginArtifact(
  caller: PluginDependency,
  dlUrl: URL
)(implicit private val repo: PluginRepoApi, localRegistry: LocalPluginRegistryApi)
  extends PluginArtifact
    with UrlBasedArtifact {

  override type R = RemotePluginArtifact

  override def usedInstaller: RepoPluginInstaller = new RepoPluginInstaller(caller.buildInfo)
}

/**
 * @param originalRemotePlugin Some - if the local plugin was originally downloaded from the marketplace,
 *                             this information is needed to check of the locally installed plugin is up-to date
 */
final case class LocalPlugin(
  caller: PluginDependency,
  descriptor: PluginDescriptor,
  root: Path,
  originalRemotePlugin: Option[RemotePluginArtifact] = None
) extends PluginArtifact {

  override type R = LocalPlugin

  override protected def usedInstaller: Installer[LocalPlugin] = LocalPluginInstaller
}