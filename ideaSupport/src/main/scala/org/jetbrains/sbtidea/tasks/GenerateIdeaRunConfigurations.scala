package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea
import org.jetbrains.sbtidea.IntellijTestRuntimeDirectories.RuntimeDirectories
import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.runIdea.IntellijVMOptionsBuilder
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
    val options = ideaConfigOptions.value
    val needsDevelopment = options.generateDefaultRunConfig || options.additionalRunConfigs.nonEmpty ||
      options.generateRunConfigForSplitMode

    if (buildRoot != projectRoot || (!needsDevelopment && !options.generateJUnitTemplate)) Def.task { () }
    else {
      // Completeness for unusual custom configurations: valid SDK metadata normally masks unused
      // directory dependencies. Select tasks dynamically: a conditional inside Def.task still
      // registers every .value dependency.
      // JUnit-only generation needs SDK context, but no development directory selection.
      val runBuilder =
        if (needsDevelopment) Def.task { intellijVMOptionsBuilder.value }
        else Def.task { baseIntellijVMOptionsBuilder.value }
      val clientDirectories: Def.Initialize[Task[Option[RuntimeDirectories]]] =
        if (options.generateRunConfigForSplitMode) Def.task {
          Some(RuntimeDirectories(
            intellijDevClientSystemDir.value.toPath,
            intellijDevClientConfigDir.value.toPath,
            intellijDevClientSystemDir.value.toPath.resolve("log"),
          ))
        } else Def.task { None }
      val tests: Def.Initialize[Task[Option[JUnitInputs]]] =
        if (options.generateJUnitTemplate) Def.task { Some(junitInputs.value) }
        else Def.task { None }

      Def.task {
        PluginLogger.bind(new SbtPluginLogger(streams.value))
        val testInputs = tests.value

        // In IntelliJ IDEA Run Configurations we don't need the explicit debug agent.
        // The standard IJ mechanism will be used to run the configuration in Run or Debug mode.
        val customVmOptions = customIntellijVMOptions.value.copy(debugInfo = None)
        val testCustomVmOptions = (Test / customIntellijVMOptions).value.copy(debugInfo = None)

        //noinspection ScalaDeprecation (workaround for SCL-25104)
        val legacyVmOptions = intellijVMOptions.value.copy(debug = false): @nowarn("cat=deprecation")
        //noinspection ScalaDeprecation (workaround for SCL-25104)
        val legacyTestVmOptions = (Test / intellijVMOptions).value.copy(debug = false): @nowarn("cat=deprecation")

        val dotIdeaFolder = baseDirectory.in(ThisBuild).value / ".idea"
        val sbtRunEnv = envVars.value
        val sbtTestEnv = envVars.in(Test).value
        val config = Some(options)
          .map(x => if (x.ideaRunEnv.isEmpty) x.copy(ideaRunEnv = sbtRunEnv) else x)
          .map(x => if (x.ideaTestEnv.isEmpty) x.copy(ideaTestEnv = sbtTestEnv) else x)
          .get
        val configBuilder = new IdeaConfigBuilder(
          projectName = name.value,
          dataDir = intellijPluginDirectory.value,
          intellijBaseDir = intellijBaseDirectory.in(ThisBuild).value,
          dotIdeaFolder = dotIdeaFolder,
          ownProductDirs = testInputs.toSeq.flatMap(_.ownProductDirs),
          testPluginRoots = testInputs.toSeq.flatMap(_.pluginRoots),
          testClasspath = testInputs.toSeq.flatMap(_.classpath),

          runIntellijVMOptions = customVmOptions,
          runLegacyIntellijVMOptions = legacyVmOptions,
          testIntellijVMOptions = testCustomVmOptions,
          testLegacyIntellijVMOptions = legacyTestVmOptions,
          intellijVMOptionsBuilder = runBuilder.value,
          useNewVmOptions = useNewVmOptions.value,
          testUseNewVmOptions = Some((Test / useNewVmOptions).value),
          testIntellijVMOptionsBuilder = testInputs.map(_.vmOptionsBuilder),
          clientRuntimeDirectories = clientDirectories.value,

          productInfoExtraDataProvider = productInfoExtraDataProvider.value,
          options = config,
        )

        configBuilder.build()
      }
    }
  }

  private case class JUnitInputs(
    ownProductDirs: Seq[File],
    pluginRoots: Seq[File],
    classpath: Seq[String],
    vmOptionsBuilder: IntellijVMOptionsBuilder,
  )

  private def junitInputs: Def.Initialize[Task[JUnitInputs]] = Def.task {
    val allPlugins = {
      val pluginDeps = intellijPlugins.all(ScopeFilter(inDependencies(ThisProject))).value.flatten
      val runtimePlugins = intellijExtraRuntimePluginsInTests.all(ScopeFilter(inDependencies(ThisProject))).value.flatten
      (pluginDeps ++ runtimePlugins).distinct
    }
    val pluginRoots = PluginClasspathUtils.collectPluginRoots(
      intellijBaseDirectory.in(ThisBuild).value.toPath,
      sbtidea.Keys.intellijBuildInfo.in(ThisBuild).value,
      allPlugins,
      new SbtPluginLogger(streams.value),
      name.value,
    ).map(_._2.toFile)
    val _ = prepareIntellijTestDirectories.value
    JUnitInputs(
      ownProductDirs = classDirectory.all(ScopeFilter(inDependencies(ThisProject), inConfigurations(Test))).value,
      pluginRoots = pluginRoots,
      classpath = TestClasspathTasks.fullTestClasspathForJUnitTemplate.value,
      vmOptionsBuilder = intellijTestVMOptionsBuilder.value,
    )
  }
}
