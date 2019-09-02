package org.jetbrains.sbtidea.download.api

import java.io.File

import org.jetbrains.sbtidea.Keys.IdeaPlugin
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.{ArtifactPart, BuildInfo}

trait IdeaInstaller {
  protected def log: PluginLogger
  protected def buildInfo: BuildInfo
  def getInstallDir: File
  def isIdeaAlreadyInstalled: Boolean
  def isPluginAlreadyInstalledAndUpdated(plugin: IdeaPlugin): Boolean
  def installIdeaDist(files: Seq[(ArtifactPart, File)]): File
  def installIdeaPlugin(plugin: IdeaPlugin, artifactPart: ArtifactPart, file: File): File
}
