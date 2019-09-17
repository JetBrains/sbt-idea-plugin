package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.sbtidea.tasks._
import sbt.Keys._
import sbt.{Def, file, _}
import ApiAdapter._

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
        mainClass in (Compile, run) := Some("com.intellij.idea.Main"),
        genRunSetting,
        fork in run := true,
        run in Compile := { packageArtifact.in(from).value; run.in(Compile).evaluated },
        javaOptions in run := createRunVMOptions(ideaTestSystemDir.in(from).value, ideaTestConfigDir.in(from).value, packageOutputDir.in(from).value),
        createIDEARunConfiguration := genCreateRunConfigurationTask(from).value,
        autoScalaLibrary := !hasPluginsWithScala(ideaExternalPlugins.all(ScopeFilter(inDependencies(from))).value.flatten)
      )


  def genCreateRunConfigurationTask(from: ProjectReference): Def.Initialize[Task[File]] = Def.task {
    val configName = "IDEA"
    val data = IdeaConfigBuilder.buildRunConfigurationXML(
      name.in(from).value,
      configName,
      name.value,
      javaOptions.in(run).value,
      ideaPluginDirectory.value)
    val outFile = baseDirectory.in(ThisBuild).value / ".idea" / "runConfigurations" / s"$configName.xml"
    IO.write(outFile, data.getBytes)
    outFile
  }

  val baseVMOptions = Seq(
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

  def createRunVMOptions(testSystem: File, testConfig: File, pluginRoot: File): Seq[String] = baseVMOptions ++ Seq(
    s"-Didea.system.path=${testSystem.getParentFile / "system"}",
    s"-Didea.config.path=${testConfig.getParentFile / "config"}",
    s"-Dplugin.path=${pluginRoot}",
    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
  )

}
