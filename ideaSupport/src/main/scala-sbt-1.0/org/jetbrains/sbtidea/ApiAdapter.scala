package org.jetbrains.sbtidea


object ApiAdapter {
  import sbt._
  import sbt.Keys._

  def genRunSetting: Def.Setting[InputTask[Unit]] = {
    run := Defaults.runTask(fullClasspath in Runtime, mainClass in run in Compile, runner in run).evaluated
  }

  def injectIntoUpdateReport(report: UpdateReport, artifacts: Seq[(Artifact, File)], module: ModuleID): UpdateReport = {
    val newConfigurationReports = report.configurations.map { report =>
      val moduleReports = report.modules :+ ModuleReport(module, artifacts.toVector, Vector.empty)
      report.withModules(moduleReports)
    }
    report.withConfigurations(newConfigurationReports)
  }
}
