package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.packaging.PackagingKeys.packageOutputDir
import org.jetbrains.sbtidea.{ClasspathStrategy, PluginLogger, SbtPluginLogger, tasks}
import sbt.Keys._
import sbt.{Def, _}

object GenerateIdeaRunConfigurations extends SbtIdeaTask[Unit] {
  override def createTask: Def.Initialize[Task[Unit]] = Def.taskDyn {
    val buildRoot = baseDirectory.in(ThisBuild).value
    val projectRoot = baseDirectory.in(ThisProject).value

    if (buildRoot == projectRoot) Def.task {
      PluginLogger.bind(new SbtPluginLogger(streams.value))
      val buildInfo = BuildInfo(
        intellijBuild.in(ThisBuild).value,
        intellijPlatform.in(ThisBuild).value)
      val actualIntellijBuild = buildInfo.getDeclaredOrActualNoSnapshotBuild(intellijBaseDirectory.in(ThisBuild).value.toPath)
      val classLoadingStrategy = ClasspathStrategy.forVersion(actualIntellijBuild)
      PluginLogger.info(s"Class loading strategy: since ${classLoadingStrategy.version}")
      val vmOptions = intellijVMOptions.value.copy(debug = false)
      val configName = name.value
      val dotIdeaFolder = baseDirectory.in(ThisBuild).value / ".idea"
      val sbtRunEnv = envVars.value
      val sbtTestEnv = envVars.in(Test).value
      val ownClassPath =
        classDirectory.all(ScopeFilter(inDependencies(ThisProject), inConfigurations(Test))).value
      val allPlugins = intellijPlugins.all(ScopeFilter(inDependencies(ThisProject))).value.flatten.distinct
      val pluginRoots =
        tasks.CreatePluginsClasspath.collectPluginRoots(
          intellijBaseDirectory.in(ThisBuild).value.toPath,
          buildInfo,
          allPlugins,
          new SbtPluginLogger(streams.value),
          name.value).map(_._2.toFile)
      val config = Some(ideaConfigOptions.value)
        .map(x => if (x.ideaRunEnv.isEmpty) x.copy(ideaRunEnv = sbtRunEnv) else  x)
        .map(x => if (x.ideaTestEnv.isEmpty) x.copy(ideaTestEnv = sbtTestEnv) else x)
        .get
      val configBuilder = new IdeaConfigBuilder(
        moduleName = name.value,
        configName = configName,
        intellijVMOptions = vmOptions,
        dataDir = intellijPluginDirectory.value,
        intellijBaseDir = intellijBaseDirectory.in(ThisBuild).value,
        dotIdeaFolder = dotIdeaFolder,
        pluginAssemblyDir = packageOutputDir.value,
        ownProductDirs = ownClassPath,
        pluginRoots = pluginRoots,
        options = config,
        classLoadingStrategy
      )

      configBuilder.build()
    } else Def.task { }
  }
}
