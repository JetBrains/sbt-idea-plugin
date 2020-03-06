package org.jetbrains.sbtidea.download.plugin
import org.jetbrains.sbtidea.Keys.IntellijPlugin
import org.jetbrains.sbtidea.download.PluginRepoUtils.getPluginDownloadURL
import org.jetbrains.sbtidea.download.api._


object PluginResolver extends Resolver[PluginDependency] {

  // TODO: resolve dependencies between plugins
  override def resolve(dep: PluginDependency): Seq[PluginArtifact] = {
    dep.plugin match {
      case IntellijPlugin.Url(url) =>
        PluginArtifact(dep, url) :: Nil
      case plugin:IntellijPlugin.Id =>
        PluginArtifact(dep, getPluginDownloadURL(dep.buildInfo, plugin)):: Nil
    }
  }

}