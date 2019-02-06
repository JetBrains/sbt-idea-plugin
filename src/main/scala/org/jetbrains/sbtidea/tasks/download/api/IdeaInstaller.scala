package org.jetbrains.sbtidea.tasks.download.api

import java.io.File

import org.jetbrains.sbtidea.Keys.IdeaPlugin
import org.jetbrains.sbtidea.tasks.download.ArtifactPart

trait IdeaInstaller {
  def getInstallDir: File
  def isIdeaAlreadyInstalled: Boolean
  def isPluginAlreadyInstalled(plugin: IdeaPlugin): Boolean
  def installIdeaDist(files: Seq[(ArtifactPart, File)]): File
  def installIdeaPlugin(plugin: IdeaPlugin, artifactPart: ArtifactPart, file: File): File
}
