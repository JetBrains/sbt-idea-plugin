package org.jetbrains.sbtidea.download.api

trait Installer[R <: ResolvedArtifact] {
  def isInstalled(art: R)(implicit ctx: IdeInstallationContext): Boolean
  def downloadAndInstall(art: R)(implicit ctx: IdeInstallationProcessContext): Unit
}