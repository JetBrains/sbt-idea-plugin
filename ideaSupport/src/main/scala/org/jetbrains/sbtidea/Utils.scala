package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.sbtidea.tasks._
import sbt.Keys._
import sbt.{Def, file, _}

trait Utils { this: Keys.type =>

  def createRunnerProject(from: ProjectReference, newProjectName: String = ""): Project = {
    val baseName: String = from match {
      case ProjectRef(_, project) => project
      case LocalProject(project)  => project
      case RootProject(build)     => build.hashCode().abs.toString
      case LocalRootProject       => "plugin"
      case ThisProject            => "plugin"
    }
    val newName = if (newProjectName.nonEmpty) newProjectName else s"$baseName-runner"
    Project(newName, file(s"target/tools/$newName"))
      .dependsOn(from % Provided)
      .settings(
        scalaVersion := scalaVersion.in(from).value,
        dumpDependencyStructure := null, // avoid cyclic dependencies on products task
        products := packageArtifact.in(from).value :: Nil,  // build the artifact when IDEA delegates "Build" action to sbt shell
        packageMethod := org.jetbrains.sbtidea.packaging.PackagingKeys.PackagingMethod.Skip(),
        unmanagedJars in Compile := intellijMainJars.value,
        unmanagedJars in Compile ++= maybeToolsJar,
        createIDEARunConfiguration := genCreateRunConfigurationTask(from).value,
        autoScalaLibrary := !bundleScalaLibrary.value
      ).enablePlugins(SbtIdeaPlugin)
  }

  def genCreateRunConfigurationTask(from: ProjectReference): Def.Initialize[Task[Unit]] = Def.task {
    PluginLogger.bind(new SbtPluginLogger(streams.value))
    val vmOptions             = intellijVMOptions.in(from).value.copy(debug = false)
    val configName            = name.in(from).value
    val dotIdeaFolder         = baseDirectory.in(ThisBuild).value / ".idea"
    val sbtRunEnv             = envVars.in(from).value
    val sbtTestEnv            = envVars.in(from, Test).value
    val config                = Some(ideaConfigOptions.value)
      .map(x => if (x.ideaRunEnv.isEmpty) x.copy(ideaRunEnv = sbtRunEnv) else x)
      .map(x => if (x.ideaTestEnv.isEmpty) x.copy(ideaTestEnv = sbtTestEnv) else x)
      .get
    val configBuilder         = new IdeaConfigBuilder(
      name.in(from).value,
      configName,
      name.value,
      vmOptions,
      intellijPluginDirectory.value,
      intellijBaseDirectory.value,
      dotIdeaFolder,
      config)

    configBuilder.build()
  }

}
