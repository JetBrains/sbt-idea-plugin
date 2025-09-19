package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea
import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.tasks.classpath.{PluginClasspathUtils, TestClasspathTasks}
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger}
import sbt.Keys.*
import sbt.{Def, *}

import scala.annotation.nowarn

private[sbtidea]
object GenerateIdeaRunConfigurations extends SbtIdeaTask[Unit] {
  override def createTask: Def.Initialize[Task[Unit]] = Def.taskDyn {
    val buildRoot = baseDirectory.in(ThisBuild).value
    val projectRoot = baseDirectory.in(ThisProject).value

    if (buildRoot == projectRoot) Def.task {
      PluginLogger.bind(new SbtPluginLogger(streams.value))
      val buildInfo = sbtidea.Keys.intellijBuildInfo.in(ThisBuild).value

      // In IntelliJ IDEA Run Configurations we don't need the explicit debug agent.
      // The standard IJ mechanism will be used to run the configuration in Run or Debug mode.
      val customVmOptions = customIntellijVMOptions.value.copy(debugInfo = None)
      val legacyVmOptions = intellijVMOptions.value.copy(debug = false): @nowarn("cat=deprecation")

      val dotIdeaFolder = baseDirectory.in(ThisBuild).value / ".idea"
      val sbtRunEnv = envVars.value
      val sbtTestEnv = envVars.in(Test).value
      val ownClassPath: Seq[File] =
        classDirectory.all(ScopeFilter(inDependencies(ThisProject), inConfigurations(Test))).value

      val fullTestClasspath = TestClasspathTasks.fullTestClasspathForJUnitTemplate.value

      val allPlugins = {
        val pluginDeps = intellijPlugins.all(ScopeFilter(inDependencies(ThisProject))).value.flatten
        val runtimePlugins = intellijExtraRuntimePluginsInTests.all(ScopeFilter(inDependencies(ThisProject))).value.flatten
        (pluginDeps ++ runtimePlugins).distinct
      }
      val pluginRoots =
        PluginClasspathUtils.collectPluginRoots(
          intellijBaseDirectory.in(ThisBuild).value.toPath,
          buildInfo,
          allPlugins,
          new SbtPluginLogger(streams.value),
          name.value
        ).map(_._2.toFile)
      val config = Some(ideaConfigOptions.value)
        .map(x => if (x.ideaRunEnv.isEmpty) x.copy(ideaRunEnv = sbtRunEnv) else x)
        .map(x => if (x.ideaTestEnv.isEmpty) x.copy(ideaTestEnv = sbtTestEnv) else x)
        .get
      val configBuilder = new IdeaConfigBuilder(
        projectName = name.value,
        dataDir = intellijPluginDirectory.value,
        intellijBaseDir = intellijBaseDirectory.in(ThisBuild).value,
        dotIdeaFolder = dotIdeaFolder,
        ownProductDirs = ownClassPath,
        testPluginRoots = pluginRoots,
        testClasspath = fullTestClasspath,

        intellijVMOptions = customVmOptions,
        legacyIntellijVMOptions = legacyVmOptions,
        intellijVMOptionsBuilder = intellijVMOptionsBuilder.value,
        useNewVmOptions = useNewVmOptions.value,

        productInfoExtraDataProvider = productInfoExtraDataProvider.value,
        options = config,
      )

      configBuilder.build()
    } else Def.task { }
  }
}
