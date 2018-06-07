package org.jetbrains.sbtidea

import org.jetbrains.sbt.tasks.PluginPackager
import org.jetbrains.sbt.tasks.PluginPackager.ProjectData
import sbt.Keys._
import sbt._

object Keys {
  lazy val ideaBuild = SettingKey[String](
    "idea-build",
    "Number of Intellij IDEA build to use in project")

  lazy val ideaDownloadDirectory = SettingKey[File](
    "idea-download-directory",
    "Directory where IDEA binaries and sources are downloaded")

  lazy val ideaInternalPlugins = SettingKey[Seq[String]](
    "idea-internal-plugins",
    "List of names of bundled Intellij IDEA plugins this project depends on")

  lazy val ideaExternalPlugins = SettingKey[Seq[IdeaPlugin]](
    "idea-external-plugins",
    "List of third-party plugins this project depends on")

  lazy val ideaEdition = SettingKey[IdeaEdition](
    "idea-edition",
    "Edition of Intellij IDEA to use in project")

  lazy val ideaDownloadSources = SettingKey[Boolean](
    "idea-download-sources",
    "Flag indicating whether IDEA sources should be downloaded too")

  lazy val ideaPublishSettings = SettingKey[PublishSettings](
    "idea-publish-settings",
    "Settings necessary for publishing IDEA plugin to plugins.jetbrains.com")

  lazy val ideaPluginFile = TaskKey[File](
    "idea-plugin-file",
    "IDEA plugin's file to publish to plugins.jetbrains.com")

  lazy val updateIdea = TaskKey[Unit](
    "update-idea",
    "Download Intellij IDEA binaries, sources and external plugins for specified build")

  lazy val publishPlugin = TaskKey[String](
    "publish-plugin",
    "Publish IDEA plugin on plugins.jetbrains.com")


  lazy val ideaBaseDirectory = TaskKey[File](
    "idea-base-directory",
    "Directory where downloaded IDEA binaries and sources are unpacked")

  lazy val ideaMainJars = TaskKey[Classpath](
    "idea-main-jars",
    "Classpath containing main IDEA jars")

  lazy val ideaInternalPluginsJars = TaskKey[Classpath](
    "idea-internal-plugins-jars",
    "Classpath containing jars of internal IDEA plugins used in this project")

  lazy val ideaExternalPluginsJars = TaskKey[Classpath](
    "idea-external-plugins-jars",
    "Classpath containing jars of external IDEA plugins used in this project")

  lazy val ideaFullJars = TaskKey[Classpath](
    "idea-full-jars",
    "Complete classpath of IDEA's and internal and external plugins' jars")

  lazy val packageMethod = SettingKey[PackagingMethod](
    "package-method",
    "What kind of artifact to produce from given project"
  )

  lazy val libraryMappings = SettingKey[Seq[(ModuleID, Option[String])]](
    "library-mappings",
    "Overrides for library mappings in artifact"
  )

  lazy val additionalFileMappings = SettingKey[Seq[(File, File)]](
    "additional-file-mappings",
    "Extra files or directories to include into the artifact"
  )

  lazy val assembleLibraries = SettingKey[Boolean](
    "assemble-libraries",
    "Should the project library dependencies be merged inside the project artifact"
  )

  lazy val pluginOutputDir = SettingKey[File](
    "plugin-output-dir",
    "Folder to write plugin artifact to"
  )

  lazy val artifactMappings = TaskKey[Map[File, File]](
    "artifact-mappings",
    "Internal structure of plugin artifact"
  )

  lazy val packagePlugin = TaskKey[File](
    "package-plugin",
    "Create plugin distribution"
  )

  lazy val dumpDependencyStructure = Def.task {
    ProjectData(
      thisProjectRef.value,
      externalDependencyClasspath.in(Compile).value,
      libraryDependencies.in(Compile).value,
      assembleLibraries.value,
      productDirectories.in(Compile).value,
      update.value,
      libraryMappings.value,
      additionalFileMappings.value,
      packageMethod.value)
  }

  sealed trait PackagingMethod

  object PackagingMethod {
    final case class Skip() extends PackagingMethod
    final case class MergeIntoParent() extends PackagingMethod
    final case class MergeIntoOther(project: Project) extends PackagingMethod
    final case class Standalone(targetPath: String = "") extends PackagingMethod
  }

  sealed trait IdeaPlugin {
    val name: String
  }

  object IdeaPlugin {
    final case class Id(name: String, id: String, channel: Option[String]) extends IdeaPlugin
    final case class Zip(name: String, url: URL) extends IdeaPlugin
    final case class Jar(name: String, url: URL) extends IdeaPlugin
  }

  sealed trait IdeaEdition {
    val name: String
    def shortname: String = name.takeRight(2)
  }

  object IdeaEdition {
    object Community extends IdeaEdition {
      override val name = "ideaIC"
    }

    object Ultimate extends IdeaEdition {
      override val name = "ideaIU"
    }
  }

  final case class PublishSettings(pluginId: String, username: String, password: String, channel: Option[String])


  lazy val buildSettings: Seq[Setting[_]] = Seq(
    ideaBuild := "LATEST-EAP-SNAPSHOT",
    ideaDownloadDirectory := baseDirectory.value / "idea",
    ideaEdition := IdeaEdition.Community,
    ideaDownloadSources := true,
    ideaBaseDirectory := ideaDownloadDirectory.value / ideaBuild.value
  )

  lazy val projectSettings: Seq[Setting[_]] = Seq(
    ideaInternalPlugins := Seq.empty,
    ideaExternalPlugins := Seq.empty,
    ideaMainJars := (ideaBaseDirectory.value / "lib" * "*.jar").classpath,
    ideaInternalPluginsJars :=
      tasks.CreatePluginsClasspath(ideaBaseDirectory.value / "plugins", ideaInternalPlugins.value),

    ideaExternalPluginsJars :=
      tasks.CreatePluginsClasspath(ideaBaseDirectory.value / "externalPlugins", ideaExternalPlugins.value.map(_.name)),

    ideaFullJars := ideaMainJars.value ++ ideaInternalPluginsJars.value ++ ideaExternalPluginsJars.value,
    unmanagedJars in Compile ++= ideaFullJars.value,

    updateIdea := tasks.UpdateIdea.apply(
      ideaBaseDirectory.value,
      ideaEdition.value,
      ideaBuild.value,
      ideaDownloadSources.value,
      ideaExternalPlugins.value,
      streams.value
    ),

    ideaPluginFile := packageBin.in(Compile).value,
    ideaPublishSettings := PublishSettings("", "", "", None),
    publishPlugin := tasks.PublishPlugin.apply(ideaPublishSettings.value, ideaPluginFile.value, streams.value),
    packageMethod := PackagingMethod.MergeIntoParent(),
    libraryMappings := "org.scala-lang"         % "scala-.*" % ".*" -> None ::
                       "org.scala-lang.modules" % "scala-.*" % ".*" -> None :: Nil,
    additionalFileMappings := Seq.empty,
    assembleLibraries := false,
    pluginOutputDir := baseDirectory.value / "artifact",
    artifactMappings := Def.taskDyn {
      val rootProject = thisProjectRef.value
      val buildDeps = buildDependencies.value
      val data = dumpDependencyStructure.all(ScopeFilter(inAnyProject)).value
      val outputDir = pluginOutputDir.value
      Def.task { PluginPackager.artifactMappings(rootProject, outputDir, data, buildDeps) }
    }.value,
    packagePlugin := Def.taskDyn {
      val mappings = artifactMappings.value
      val outputDir = pluginOutputDir.value
      Def.task{ IO.delete(outputDir); PluginPackager.packageArtifact(mappings); outputDir }
    }.value,
    aggregate.in(artifactMappings) := false,
    aggregate.in(packagePlugin) := false,
    unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"
  )

}
