package org.jetbrains.sbtidea.download.plugin

import java.nio.file.Path

trait PluginIndex {
  def put(descriptor: PluginDescriptor, installPath: Path): Unit
  def contains(id: String): Boolean
  def getInstallRoot(id: String): Option[Path]
  def getPluginDescriptor(id: String): Option[PluginDescriptor]
  def getAllDescriptors: Seq[PluginDescriptor]
}