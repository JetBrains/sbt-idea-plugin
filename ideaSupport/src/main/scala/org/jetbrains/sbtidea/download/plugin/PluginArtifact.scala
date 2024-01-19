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

final case class LocalPlugin(
  caller: PluginDependency,
  descriptor: PluginDescriptor,
  root: Path
) extends PluginArtifact {

  override type R = LocalPlugin

  override protected def usedInstaller: Installer[LocalPlugin] = LocalPluginInstaller
}

object LocalPluginInstaller extends Installer[LocalPlugin] {
  override def isInstalled(art: LocalPlugin)(implicit ctx: InstallContext): Boolean = art.root.toFile.exists()

  override def downloadAndInstall(art: LocalPlugin)(implicit ctx: InstallContext): Unit = ()
}