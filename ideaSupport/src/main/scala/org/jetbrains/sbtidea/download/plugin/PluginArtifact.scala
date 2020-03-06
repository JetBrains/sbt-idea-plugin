package org.jetbrains.sbtidea.download.plugin

import java.net.URL

import org.jetbrains.sbtidea.download.api._


case class PluginArtifact(caller: PluginDependency, dlUrl: URL) extends ResolvedArtifact with UrlBasedArtifact {
  override type R = PluginArtifact
  override def usedInstaller: PluginInstaller = new PluginInstaller(caller.buildInfo)
}