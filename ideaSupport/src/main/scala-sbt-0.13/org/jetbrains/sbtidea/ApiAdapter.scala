package org.jetbrains.sbtidea

object ApiAdapter {
  import sbt._
  import sbt.Keys._

  def genRunSetting: Def.Setting[InputTask[Unit]] = {
    run := Defaults.runTask(fullClasspath in Runtime, mainClass in run in Compile, runner in run).inputTaskValue
  }
}
