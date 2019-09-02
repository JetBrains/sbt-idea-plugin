package org.jetbrains.sbtidea.download.api

case class PluginMetadata(id: String, version: String, sinceBuild: String, untilBuild: String, channel: String = "")

