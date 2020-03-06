package org.jetbrains.sbtidea.download.api

trait Installer[R <: ResolvedArtifact] {
  def isInstalled(art: R)(implicit ctx: InstallContext): Boolean
  def downloadAndInstall(art: R)(implicit ctx: InstallContext): Unit
}