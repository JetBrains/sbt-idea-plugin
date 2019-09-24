package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.sbtidea.tasks._
import sbt.Keys._
import sbt.{Def, file, _}
import ApiAdapter._
import org.jetbrains.sbtidea.runIdea.{IdeaRunner, IdeaVMOptions}

trait Utils { this: Keys.type =>

  private lazy val ivyHomeDir: File = Option(System.getProperty("sbt.ivy.home")).fold(homePrefix / ".ivy2")(file)

  def createRunnerProject(from: ProjectReference, newProjectName: String = ""): Project =
    Project(newProjectName, file(s"target/tools/$newProjectName"))
      .dependsOn(from % Provided)
      .settings(
        name := { if (newProjectName.nonEmpty) newProjectName else name.in(from) + "-runner"},
        scalaVersion := scalaVersion.in(from).value,
        dumpDependencyStructure := null, // avoid cyclic dependencies on products task
        products := packageArtifact.in(from).value :: Nil,  // build the artifact when IDEA delegates "Build" action to sbt shell
        packageMethod := org.jetbrains.sbtidea.packaging.PackagingKeys.PackagingMethod.Skip(),
        unmanagedJars in Compile := ideaMainJars.value,
        unmanagedJars in Compile ++= maybeToolsJar,
        autoScalaLibrary := !hasPluginsWithScala(ideaExternalPlugins.all(ScopeFilter(inDependencies(from))).value.flatten)
      )


  def genCreateRunConfigurationTask: Def.Initialize[Task[File]] = Def.task {
    implicit  val log: PluginLogger = new SbtPluginLogger(streams.value)
    val configName = "IDEA"
    val vmOptions = ideaVMOptions.value.copy(debug = false)
    val runner = new IdeaRunner(ideaMainJars.value.map(_.data.toPath), packageOutputDir.value.toPath, vmOptions)
    val data = IdeaConfigBuilder.buildRunConfigurationXML(
      name.value,
      configName,
      name.value,
      runner.vmOptionsSeq,
      ideaPluginDirectory.value)
    val outFile = baseDirectory.in(ThisBuild).value / ".idea" / "runConfigurations" / s"$configName.xml"
    IO.write(outFile, data.getBytes)
    outFile
  }

  private val baseVMOptions = Seq(
    "-Xms256m",
    "-Xmx2048m",
    "-server",
    "-ea",
    s"-Dsbt.ivy.home=$ivyHomeDir"
  )

  def createTestVMOptions(testSystem: File, testConfig: File, pluginRoot: File): Seq[String] = baseVMOptions ++ Seq(
    s"-Didea.system.path=$testSystem",
    s"-Didea.config.path=$testConfig",
    s"-Dplugin.path=$pluginRoot"
  )

}
