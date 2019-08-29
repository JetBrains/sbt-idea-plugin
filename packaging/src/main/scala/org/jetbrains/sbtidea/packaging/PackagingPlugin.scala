package org.jetbrains.sbtidea.packaging

import sbt._

object PackagingPlugin extends AutoPlugin {
  val autoImport: PackagingKeys.type = PackagingKeys
  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin
  override def projectSettings: Seq[Setting[_]] = PackagingKeys.projectSettings
}
