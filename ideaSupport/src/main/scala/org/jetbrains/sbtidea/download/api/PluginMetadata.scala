package org.jetbrains.sbtidea.download.api

/**
  * Maps to necessary fields in plugin.xml
  */
case class PluginMetadata(id: String,
                          name: String,
                          version: String,
                          sinceBuild: String,
                          untilBuild: String)


