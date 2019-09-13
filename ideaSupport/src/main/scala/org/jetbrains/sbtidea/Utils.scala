package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.sbtidea.tasks._
import sbt.Keys._
import sbt.{file, _}

trait Utils { this: Keys.type =>

  private val pluginsWithScala = Seq( // TODO: add more
    "org.intellij.scala",
    "org.jetbrains.plugins.hocon",
    "intellij.haskell"
  )

  def createRunnerProject(from: ProjectReference, newProjectName: String): Project =
    Project(newProjectName, file(s"target/tools/$newProjectName"))
      .dependsOn(from % Provided)
      .settings(
        name := newProjectName,
        scalaVersion := scalaVersion.in(from).value,
        dumpDependencyStructure := null, // avoid cyclic dependencies on products task
        products := packageArtifactDynamic.in(from).value :: Nil,
        packageMethod := org.jetbrains.sbtidea.packaging.PackagingKeys.PackagingMethod.Skip(),
        unmanagedJars in Compile := ideaMainJars.value,
        unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",
        mainClass in (Compile, run) := Some("com.intellij.idea.Main"),
        javaOptions in run := javaOptions.in(from, Test).value :+
          "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",
        createIDEARunConfiguration := {
          val configName = "IDEA"
          val data = IdeaConfigBuilder.buildRunConfigurationXML(
            name.in(from).value,
            configName,
            newProjectName,
            javaOptions.in(from, Test).value,
            ideaPluginDirectory.value)
          val outFile = baseDirectory.in(ThisBuild).value / ".idea" / "runConfigurations" / s"$configName.xml"
          IO.write(outFile, data.getBytes)
          outFile
        },
        autoScalaLibrary := {
          val allPlugins = ideaExternalPlugins.all(ScopeFilter(inDependencies(from))).value.flatten
          !allPlugins.exists(plugin => pluginsWithScala.exists(id => plugin.toString.matches(s".*$id.*")))
        }
      ).enablePlugins(SbtIdeaPlugin)

}
