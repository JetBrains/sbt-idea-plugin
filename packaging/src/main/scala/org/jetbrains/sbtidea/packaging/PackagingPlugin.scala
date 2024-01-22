package org.jetbrains.sbtidea.packaging

import sbt.*

object PackagingPlugin extends AutoPlugin {
  val autoImport: PackagingKeys.type = PackagingKeys

  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin
  override def projectSettings: Seq[Setting[?]] = PackagingKeys.projectSettings
}
