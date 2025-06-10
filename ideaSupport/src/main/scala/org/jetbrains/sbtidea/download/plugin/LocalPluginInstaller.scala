package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.download.api.{IdeInstallationContext, IdeInstallationProcessContext, Installer}

object LocalPluginInstaller extends Installer[LocalPlugin] {
  override def isInstalled(art: LocalPlugin)(implicit ctx: IdeInstallationContext): Boolean =
    art.root.toFile.exists() && art.originalRemotePlugin.forall(_.isInstalled)

  override def downloadAndInstall(art: LocalPlugin)(implicit ctx: IdeInstallationProcessContext): Unit =
    art.originalRemotePlugin match {
      case None => //ok, do nothing
      case Some(remotePlugin) =>
        remotePlugin.install()
    }
}