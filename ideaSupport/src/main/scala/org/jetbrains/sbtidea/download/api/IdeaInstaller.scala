package org.jetbrains.sbtidea.download.api

import java.nio.file.Path

import org.jetbrains.sbtidea.Keys.IdeaPlugin
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.{ArtifactPart, BuildInfo}

trait IdeaInstaller {
  protected def log: PluginLogger
  protected def buildInfo: BuildInfo
  def getInstallDir: Path
  def isIdeaAlreadyInstalled: Boolean
  def isPluginAlreadyInstalledAndUpdated(plugin: IdeaPlugin): Boolean
  def installIdeaDist(files: Seq[(ArtifactPart, Path)]): Path
  def installIdeaPlugin(plugin: IdeaPlugin, file: Path): Path
}
