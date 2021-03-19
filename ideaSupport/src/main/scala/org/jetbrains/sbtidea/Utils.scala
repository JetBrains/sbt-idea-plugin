package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.packaging.PackagingKeys._
import sbt.Keys._
import sbt.{file, _}

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

}
