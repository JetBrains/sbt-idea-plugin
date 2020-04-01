package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.Keys.IntellijPlugin
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.api._


case class PluginDependency(plugin: IntellijPlugin,
                             buildInfo: BuildInfo,
                             dependsOn: Seq[UnresolvedArtifact] = Seq.empty)
                           (implicit private val ctx: InstallContext, repo: PluginRepoApi, localRegistry: LocalPluginRegistryApi) extends UnresolvedArtifact {
  override type U = PluginDependency
  override type R = PluginArtifact
  override protected def usedResolver: PluginResolver = new PluginResolver(resolveSettings = plugin.resolveSettings)
  override def toString: String = s"PluginDependency($plugin)"
}