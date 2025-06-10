package org.jetbrains.sbtidea.download.api

trait Installer[R <: ResolvedArtifact] {
  /**
   * @return true if the plugin is already installed locally (and optionally up to date)
   * @note for some artifacts and installers it can potentially check if the installed artifact is up-to date.
   *       For example, if a local plugin was originally downloaded from the marketplace
   *       and a newer version is available (compatible with the current build), the newer version will be downloaded.
   */
  def isInstalled(art: R)(implicit ctx: IdeInstallationContext): Boolean
  def downloadAndInstall(art: R)(implicit ctx: IdeInstallationProcessContext): Unit
}