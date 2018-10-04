package org.jetbrains.sbtidea

import org.jetbrains.sbtidea
import sbt.{AutoPlugin, Setting, plugins}

object SbtIdeaPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  val autoImport: Keys.type = sbtidea.Keys
  override def buildSettings: Seq[Setting[_]] = Keys.buildSettings
  override def projectSettings: Seq[Setting[_]] = Keys.projectSettings
}
