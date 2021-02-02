package org.jetbrains.sbtidea

import org.jetbrains.sbtidea
import org.jetbrains.sbtidea.packaging.PackagingPlugin
import org.jetbrains.sbtidea.tasks.structure.render.ProjectStructureVisualizerPlugin
import sbt.{AutoPlugin, Def, Setting}

abstract class AbstractSbtIdeaPlugin extends AutoPlugin {
  val autoImport: Keys.type = sbtidea.Keys
  override def requires = PackagingPlugin && ProjectStructureVisualizerPlugin
  override def globalSettings: Seq[Def.Setting[_]] = Keys.globalSettings
  override def buildSettings: Seq[Setting[_]]   = Keys.buildSettings
  override def projectSettings: Seq[Setting[_]] = Keys.projectSettings
}

object SbtIdeaPlugin extends AbstractSbtIdeaPlugin