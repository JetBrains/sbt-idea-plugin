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

  /**
   * Reminder, this is only the "External dependencies classpath"
   * The "full classpath" will also contain these entries before
   *  1. Current plugin jars
   *  1. Modules test-classes
   *  1. All the test libraries from repo caches
   *
   * @note Similar logic for Gradle IntelliJ plugin is located here:<br>
   *       https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/main/src/main/kotlin/org/jetbrains/intellij/platform/gradle/tasks/TestIdeTask.kt
   * @note Reminder: the "full classpath" will be reused in [[org.jetbrains.sbtidea.tasks.IdeaConfigBuilder]] in `buildTestClasspath` method
   * @see https://youtrack.jetbrains.com/issue/IJPL-180516
   */
  def test: Def.Initialize[Task[Classpath]] = Def.task {
    val mainClasspath = SbtIdeaKeys.intellijMainJarsClasspath.value
    val pluginsClasspath = SbtIdeaKeys.intellijPluginJarsClasspath.value.flatMap(_._2)
    val testClasspath = SbtIdeaKeys.intellijTestJarsClasspath.value
    val intellijExtraPluginsInTestsClasspath = SbtIdeaKeys.intellijExtraRuntimePluginsJarsInTestsClasspath.value.flatMap(_._2)

    // Keep the plugins classpath before the app system classpath to better emulate the production behavior.
     mainClasspath ++ testClasspath ++ pluginsClasspath ++ intellijExtraPluginsInTestsClasspath
  }
}
