package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea
import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.packaging.PackagingKeys.packageOutputDir
import org.jetbrains.sbtidea.tasks.classpath.PluginClasspathUtils
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger}
import org.jetbrains.sbtidea.packaging.hasProdTestSeparationEnabled
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
      val configName = name.value
      val dotIdeaFolder = baseDirectory.in(ThisBuild).value / ".idea"
      val sbtRunEnv = envVars.value
      val sbtTestEnv = envVars.in(Test).value
      val ownClassPath: Seq[File] =
        classDirectory.all(ScopeFilter(inDependencies(ThisProject), inConfigurations(Test))).value
      val managedTestClasspath: Seq[File] =
        managedClasspath.all(ScopeFilter(inDependencies(ThisProject), inConfigurations(Test))).value
          .flatMap(_.map(_.data))
          .distinct
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
      val projectName = name.value
      val moduleName =
        if (hasProdTestSeparationEnabled) s"$projectName.main"
        else projectName
      val configBuilder = new IdeaConfigBuilder(
        moduleName = moduleName,
        configName = configName,
        intellijVMOptions = vmOptions,
        dataDir = intellijPluginDirectory.value,
        intellijBaseDir = intellijBaseDirectory.in(ThisBuild).value,
        productInfoExtraDataProvider.value,
        dotIdeaFolder = dotIdeaFolder,
        pluginAssemblyDir = packageOutputDir.value,
        ownProductDirs = ownClassPath,
        testPluginRoots = pluginRoots,
        extraJUnitTemplateClasspath = managedTestClasspath,
        options = config,
      )

      configBuilder.build()
    } else Def.task { }
  }
}
