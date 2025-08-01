package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea
import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.tasks.classpath.PluginClasspathUtils
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger}
import sbt.Keys.*
import sbt.{Def, *}

object GenerateIdeaRunConfigurations extends SbtIdeaTask[Unit] {
  override def createTask: Def.Initialize[Task[Unit]] = Def.taskDyn {
    val buildRoot = baseDirectory.in(ThisBuild).value
    val projectRoot = baseDirectory.in(ThisProject).value

    if (buildRoot == projectRoot) Def.task {
      PluginLogger.bind(new SbtPluginLogger(streams.value))
      val buildInfo = sbtidea.Keys.intellijBuildInfo.in(ThisBuild).value
      val vmOptions = intellijVMOptions.value.copy(debug = false)
      val dotIdeaFolder = baseDirectory.in(ThisBuild).value / ".idea"
      val sbtRunEnv = envVars.value
      val sbtTestEnv = envVars.in(Test).value
      val ownClassPath: Seq[File] =
        classDirectory.all(ScopeFilter(inDependencies(ThisProject), inConfigurations(Test))).value

      val fullTestClasspath: Seq[File] =
        (Test / fullClasspath).value.map(_.data).distinct

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
        .map(x => if (x.ideaRunEnv.isEmpty) x.copy(ideaRunEnv = sbtRunEnv) else  x)
        .map(x => if (x.ideaTestEnv.isEmpty) x.copy(ideaTestEnv = sbtTestEnv) else x)
        .get
      val configBuilder = new IdeaConfigBuilder(
        projectName = name.value,
        intellijVMOptions = vmOptions,
        dataDir = intellijPluginDirectory.value,
        intellijBaseDir = intellijBaseDirectory.in(ThisBuild).value,
        productInfoExtraDataProvider.value,
        dotIdeaFolder = dotIdeaFolder,
        ownProductDirs = ownClassPath,
        testPluginRoots = pluginRoots,
        options = config,
        testClasspath = fullTestClasspath
      )

      configBuilder.build()
    } else Def.task { }
  }
}
