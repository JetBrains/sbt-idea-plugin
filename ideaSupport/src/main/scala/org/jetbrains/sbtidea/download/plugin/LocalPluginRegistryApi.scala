package org.jetbrains.sbtidea.download.plugin
import java.nio.file.Path

import org.jetbrains.sbtidea.IntellijPlugin

trait LocalPluginRegistryApi {

  def getPluginDescriptor(ideaPlugin: IntellijPlugin): Either[String, PluginDescriptor]

  def markPluginInstalled(ideaPlugin: IntellijPlugin, to: Path): Unit

  def isPluginInstalled(ideaPlugin: IntellijPlugin): Boolean

  def getInstalledPluginRoot(ideaPlugin: IntellijPlugin): Path

  def getAllDescriptors: Seq[PluginDescriptor]
}
