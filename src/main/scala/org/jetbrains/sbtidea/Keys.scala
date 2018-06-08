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

  lazy val packageAdditionalProjects = SettingKey[Seq[Project]](
    "package-additional-projects",
    "Projects to package alongside current, without adding classpath dependencies"
  )

  lazy val packageLibraryMappings = SettingKey[Seq[(ModuleID, Option[String])]](
    "package-library-mappings",
    "Overrides for library mappings in artifact"
  )

  lazy val packageFileMappings = SettingKey[Seq[(File, String)]](
    "package-file-mappings",
    "Extra files or directories to include into the artifact"
  )

  lazy val packageAssembleLibraries = SettingKey[Boolean](
    "package-assemble-libraries",
    "Should the project library dependencies be merged inside the project artifact"
  )

  lazy val packageOutputDir = SettingKey[File](
    "package-output-dir",
    "Folder to write plugin artifact to"
  )

  lazy val packageMappings = TaskKey[Map[File, File]](
    "package-mappings",
    "Internal structure of plugin artifact"
  )

  lazy val packagePlugin = TaskKey[File](
    "package-plugin",
    "Create plugin distribution"
  )

  lazy val dumpDependencyStructure = Def.task {
    ProjectData(
      thisProjectRef.value,
      managedClasspath.in(Compile).value,
      libraryDependencies.in(Compile).value,
      packageAdditionalProjects.value,
      packageAssembleLibraries.value,
      productDirectories.in(Compile).value,
      update.value,
      packageLibraryMappings.value,
      packageFileMappings.value,
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
    ideaBaseDirectory := ideaDownloadDirectory.value / ideaBuild.value,
    onLoad in Global := ((s: State) => {
      "updateIdea" :: s
    }) compose (onLoad in Global).value
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
    packageLibraryMappings := "org.scala-lang"         % "scala-.*" % ".*" -> None ::
                              "org.scala-lang.modules" % "scala-.*" % ".*" -> None :: Nil,
    packageFileMappings := Seq.empty,
    packageAdditionalProjects := Seq.empty,
    packageAssembleLibraries := false,
    packageOutputDir := baseDirectory.value / "artifact",
    packageMappings := Def.taskDyn {
      val rootProject = thisProjectRef.value
      val buildDeps = buildDependencies.value
      val data = dumpDependencyStructure.all(ScopeFilter(inAnyProject)).value
      val outputDir = packageOutputDir.value
//      compile.in(packageBin).all(ScopeFilter(inAnyProject)).value
      Def.task { PluginPackager.artifactMappings(rootProject, outputDir, data, buildDeps) }
    }.value,
    packagePlugin := Def.taskDyn {
      val outputDir = packageOutputDir.value
      val mappings  = packageMappings.value
      Def.task{ IO.delete(outputDir); PluginPackager.packageArtifact(mappings); outputDir }
    }.value,
    aggregate.in(packageMappings) := false,
    aggregate.in(packagePlugin) := false,
    aggregate.in(updateIdea) := false,
    unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar"
  )

}
