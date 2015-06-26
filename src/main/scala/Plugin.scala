package com.dancingrobot84.sbtidea

import sbt._

object SbtIdeaPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  val autoImport = com.dancingrobot84.sbtidea.Keys
}

