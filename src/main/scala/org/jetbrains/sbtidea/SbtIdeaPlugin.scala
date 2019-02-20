package org.jetbrains.sbtidea

import org.jetbrains.sbtidea
import sbt.{AutoPlugin, Setting, plugins}

abstract class AbstractSbtIdeaPlugin extends AutoPlugin {
  val autoImport: Keys.type = sbtidea.Keys
  override def requires = plugins.JvmPlugin
  override def globalSettings: Seq[Setting[_]]  = Keys.globalSettings
  override def buildSettings: Seq[Setting[_]]   = Keys.buildSettings
  override def projectSettings: Seq[Setting[_]] = Keys.projectSettings
}

object SbtIdeaPlugin extends AbstractSbtIdeaPlugin