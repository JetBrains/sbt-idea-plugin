package org.jetbrains.sbtidea.tasks.structure.render

import org.jetbrains.sbtidea.SbtPluginLogger
import org.jetbrains.sbtidea.structure.sbtImpl.{SbtProjectData, SbtProjectStructureExtractor}
import sbt.KeyRanks.Invisible
import sbt._
import sbt.Keys._

object ProjectStructureVisualizerPlugin extends AutoPlugin {
  import autoImport._
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  override def projectSettings: Seq[Setting[_]] = Seq(
    printProjectGraph := {
      val rootProject = thisProjectRef.value
      val buildDeps = buildDependencies.value
      val data = dumpBuildStructureCore.?.all(ScopeFilter(inAnyProject)).value.flatten.filterNot(_ == null)
      val logger = new SbtPluginLogger(streams.value)
      val structure = new SbtProjectStructureExtractor(rootProject, data, buildDeps, logger).extract
      streams.value.log.info(new StructurePrinter().renderASCII(structure.last))
    },
    dumpBuildStructureCore := {
      SbtProjectData(
        thisProjectRef.value,
        name.value,
        managedClasspath.in(Compile).value,
        libraryDependencies.in(Compile).value,
        productDirectories.in(Compile).value,
        update.value
      )
    }
  )

  object autoImport {
    lazy val dumpBuildStructureCore = taskKey[SbtProjectData]("")//.withRank(Invisible)
    lazy val printProjectGraph      = taskKey[Unit]("Show ASCII project dependency graph in console")
  }
}
