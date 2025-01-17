package org.jetbrains.sbtidea.download.api

trait ResolvedArtifact {
  type R >: this.type <: ResolvedArtifact

  protected def usedInstaller: Installer[R]

  def isInstalled(implicit ctx: IdeInstallationContext): Boolean     = usedInstaller.isInstalled(this)
  def install    (implicit ctx: IdeInstallationProcessContext): Unit = usedInstaller.downloadAndInstall(this)
}