package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download.{BuildInfo, VersionComparatorUtil}
import org.jetbrains.sbtidea.download.plugin.LocalPluginRegistry
import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.sbtidea.tasks._
import sbt.Keys._
import sbt.{Def, file, _}

trait Utils { this: Keys.type =>

  /**
    * Runner projects were required to generate a synthetic IJ module with only IJ platform jars for the run configurations
    * to work. Since run configuration classpaths are now calculated statically, runner projects are no longer necessary.
    */
  @deprecated("runner projects are not required anymore", "3.8.0")
  def createRunnerProject(from: ProjectReference, newProjectName: String = ""): Project = {
    val baseName: String = from match {
      case ProjectRef(_, project) => project
      case LocalProject(project)  => project
      case RootProject(build)     => build.hashCode().abs.toString
      case LocalRootProject       => "plugin"
      case ThisProject            => "plugin"
    }
    println("Runner projects are deprecated, see createRunnerProject documentation")
    val newName = if (newProjectName.nonEmpty) newProjectName else s"$baseName-runner"
    Project(newName, file(s"target/tools/$newName"))
      .dependsOn(from % Provided)
      .settings(
        scalaVersion := scalaVersion.in(from).value,
        dumpDependencyStructure := null, // avoid cyclic dependencies on products task
        products := packageArtifact.in(from).value :: Nil,  // build the artifact when IDEA delegates "Build" action to sbt shell
        packageMethod := org.jetbrains.sbtidea.packaging.PackagingKeys.PackagingMethod.Skip(),
        unmanagedJars in Compile := intellijMainJars.value,
        autoScalaLibrary := !bundleScalaLibrary.value
      ).enablePlugins(SbtIdeaPlugin)
  }

  def genCreateRunConfigurationTask: Def.Initialize[Task[Unit]] = Def.taskDyn {
    val buildRoot = baseDirectory.in(ThisBuild).value
    val projectRoot = baseDirectory.in(ThisProject).value

    if (buildRoot == projectRoot) Def.task {
      PluginLogger.bind(new SbtPluginLogger(streams.value))
      val newClassLoadingStrategy = VersionComparatorUtil.compare(intellijBuild.value, newClassloadingSinceVersion) >= 0
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
          BuildInfo(
            intellijBuild.in(ThisBuild).value,
            intellijPlatform.in(ThisBuild).value
          ),
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
        ideaBaseDir = intellijBaseDirectory.value,
        dotIdeaFolder = dotIdeaFolder,
        pluginAssemblyDir = packageOutputDir.value,
        ownProductDirs = ownClassPath,
        intellijDir = intellijBaseDirectory.in(ThisBuild).value,
        pluginRoots = pluginRoots,
        options = config,
        newClasspathStrategy = newClassLoadingStrategy)

      configBuilder.build()
    } else Def.task { }
  }

}
