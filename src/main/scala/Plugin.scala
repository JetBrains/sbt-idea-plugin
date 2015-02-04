package ideaplugin

import sbt._

object Plugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  val autoImport = ideaplugin.Keys
}

