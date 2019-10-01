package org.jetbrains.sbtidea.download.api

import java.nio.file.Path

import org.jetbrains.sbtidea.Keys.IntellijPlugin
import org.jetbrains.sbtidea.LogAware
import org.jetbrains.sbtidea.download.{ArtifactPart, BuildInfo}

trait IdeaInstaller extends LogAware {
  protected def buildInfo: BuildInfo
  def getInstallDir: Path
  def isIdeaAlreadyInstalled: Boolean
  def isPluginAlreadyInstalledAndUpdated(plugin: IntellijPlugin): Boolean
  def installIdeaDist(files: Seq[(ArtifactPart, Path)]): Path
  def installIdeaPlugin(plugin: IntellijPlugin, file: Path): Path
}
