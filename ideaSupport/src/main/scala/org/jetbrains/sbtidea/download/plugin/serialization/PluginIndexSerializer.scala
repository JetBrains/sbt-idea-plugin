package org.jetbrains.sbtidea.download.plugin.serialization

import org.jetbrains.sbtidea.download.plugin.PluginIndexImpl.PluginInfo

import java.nio.file.Path
import scala.collection.Map

trait PluginIndexSerializer {
  def load(file: Path): Map[String, PluginInfo]

  def save(file: Path, data: Map[String, PluginInfo]): Unit
}