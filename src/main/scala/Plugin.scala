package com.dancingrobot84.sbtidea

import sbt._

object SbtIdeaPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  val autoImport = com.dancingrobot84.sbtidea.Keys
  override def buildSettings: Seq[Setting[_]] = Keys.buildSettings
  override def projectSettings: Seq[Setting[_]] = Keys.projectSettings
}

