package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download.plugin.PluginDescriptor

import java.io.File

case class PluginJars(descriptor: PluginDescriptor, pluginRoot: File, pluginJars: Seq[File])