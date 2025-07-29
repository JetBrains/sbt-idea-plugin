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

  // Reminder, this is an "External dependencies classpath"
  // The full classpath will also contain these entries before
  //  1. Current plugin jars
  //  2. Modules test-classes
  //  3. All the test libraries from repo caches
  //
  // TODO: unify with org.jetbrains.sbtidea.tasks.IdeaConfigBuilder.buildTestClasspath
  def test: Def.Initialize[Task[Classpath]] = Def.task {
    val mainClasspath = SbtIdeaKeys.intellijMainJarsClasspath.value
    val pluginsClasspath = SbtIdeaKeys.intellijPluginJarsClasspath.value.flatMap(_._2)
    val testClasspath = SbtIdeaKeys.intellijTestJarsClasspath.value
    val intellijExtraPluginsInTestsClasspath = SbtIdeaKeys.intellijExtraRuntimePluginsJarsInTestsClasspath.value.flatMap(_._2)

    // Keep the plugins classpath before the app system classpath to better emulate the production behavior.
    pluginsClasspath ++ intellijExtraPluginsInTestsClasspath ++ mainClasspath ++ testClasspath
  }
}
