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
        autoScalaLibrary := !hasPluginsWithScala(intellijPlugins.?.all(ScopeFilter(inDependencies(from))).value.flatten.flatten)
      ).enablePlugins(SbtIdeaPlugin)
  }

  def genCreateRunConfigurationTask(from: ProjectReference): Def.Initialize[Task[Unit]] = Def.task {
    implicit  val log: PluginLogger = new SbtPluginLogger(streams.value)
    val createJunitTemplate = generateJUnitTemplate.value
    val configName = name.in(from).value
    val vmOptions = intellijVMOptions.in(from).value.copy(debug = false)
    val configBuilder = new IdeaConfigBuilder(
      name.in(from).value,
      configName,
      name.value,
      vmOptions,
      intellijPluginDirectory.value,
      intellijBaseDirectory.value)
    val runConfigContent = configBuilder.buildRunConfigurationXML
    val outFile = baseDirectory.in(ThisBuild).value / ".idea" / "runConfigurations" / s"$configName.xml"
    IO.write(outFile, runConfigContent.getBytes)
    if (createJunitTemplate) {
      val templateContent = configBuilder.buildJUnitTemplate
      val outFile = baseDirectory.in(ThisBuild).value / ".idea" / "runConfigurations" / "_template__of_JUnit.xml"
      IO.write(outFile, templateContent.getBytes)
    }
  }

}
