package org.jetbrains.sbtidea.download.plugin.serialization

import org.jetbrains.sbtidea.download.plugin.PluginInfo

import java.nio.file.Path

trait PluginIndexSerializer {
  def load(file: Path): Seq[(String, PluginInfo)]

  def save(file: Path, data: Seq[(String, PluginInfo)]): Unit
}