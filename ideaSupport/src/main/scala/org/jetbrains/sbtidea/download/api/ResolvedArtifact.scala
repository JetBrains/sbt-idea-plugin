package org.jetbrains.sbtidea.download.api

trait ResolvedArtifact {
  type R >: this.type <: ResolvedArtifact

  protected def usedInstaller: Installer[R]

  def isInstalled(implicit ctx: InstallContext): Boolean   = usedInstaller.isInstalled(this)
  def install    (implicit ctx: InstallContext): Unit      = usedInstaller.downloadAndInstall(this)
}