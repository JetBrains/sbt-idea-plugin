package org.jetbrains.sbtidea

import sbt.{ConfigurationReport, ModuleReport, UpdateReport}

object ApiAdapter {
  import sbt._
  import sbt.Keys._

  def genRunSetting: Def.Setting[InputTask[Unit]] = {
    run := Defaults.runTask(fullClasspath in Runtime, mainClass in run in Compile, runner in run).inputTaskValue
  }

  def injectIntoUpdateReport(report: UpdateReport, injectInto: Configuration, artifacts: Seq[(Artifact, File)], module: ModuleID): UpdateReport = {
    val newConfigs = report.configurations.map {c =>
      if (c.configuration == injectInto.name) {
        val m = c.modules :+ ModuleReport(module, artifacts.toVector, Vector.empty)
        new ConfigurationReport(c.configuration, m, c.details, c.evicted)
      } else c
    }
    new UpdateReport(report.cachedDescriptor,report.configurations, report.stats, sbt.jetbrains.BadCitizen.extractStamps(report))
  }

}
