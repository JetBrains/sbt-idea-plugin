package org.jetbrains.sbtidea.tasks.classpath

import org.jetbrains.sbtidea.Keys as SbtIdeaKeys
import sbt.Keys.Classpath
import sbt.{Def, Task}

object ExternalDependencyClasspathTasks {

  def main: Def.Initialize[Task[Classpath]] = Def.task {
    val mainClasspath = SbtIdeaKeys.intellijMainJarsClasspath.value
    val pluginsClasspath = SbtIdeaKeys.intellijPluginJarsClasspath.value.flatMap(_._2)
    mainClasspath ++ pluginsClasspath
  }

  def test: Def.Initialize[Task[Classpath]] = Def.task {
    val mainClasspath = SbtIdeaKeys.intellijMainJarsClasspath.value
    val pluginsClasspath = SbtIdeaKeys.intellijPluginJarsClasspath.value.flatMap(_._2)
    val testClasspath = SbtIdeaKeys.intellijTestJarsClasspath.value
    val intellijExtraPluginsInTestsClasspath = SbtIdeaKeys.intellijExtraRuntimePluginsJarsInTestsClasspath.value.flatMap(_._2)
    mainClasspath ++ pluginsClasspath ++ testClasspath ++ intellijExtraPluginsInTestsClasspath
  }
}
