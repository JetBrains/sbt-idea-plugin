package org.jetbrains.sbtidea.download.plugin
import java.nio.file.Path

import org.jetbrains.sbtidea.Keys

trait LocalPluginRegistryApi {

  def getPluginDescriptor(ideaPlugin: Keys.IntellijPlugin): Either[String, PluginDescriptor]

  def markPluginInstalled(ideaPlugin: Keys.IntellijPlugin, to: Path): Unit

  def isPluginInstalled(ideaPlugin: Keys.IntellijPlugin): Boolean

  def getInstalledPluginRoot(ideaPlugin: Keys.IntellijPlugin): Path

  def getAllDescriptors: Seq[PluginDescriptor]
}
