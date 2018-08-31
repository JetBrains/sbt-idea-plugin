package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.tasks.packaging._
import org.jetbrains.sbtidea.tasks.packaging.artifact._
import sbt.Keys._
import sbt._

object Keys {

  lazy val ideaPluginName = SettingKey[String](
    "idea-plugin-name",
    "Name of the plugin you're developing")

  lazy val ideaBuild = SettingKey[String](
    "idea-build",
    "Number of IntelliJ IDEA build to use in project")

  lazy val ideaDownloadDirectory = SettingKey[File](
    "idea-download-directory",
    "Directory where IDEA binaries and sources are downloaded")

  lazy val ideaInternalPlugins = SettingKey[Seq[String]](
    "idea-internal-plugins",
    "List of names of bundled IntelliJ IDEA plugins this project depends on")

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

  lazy val ideaPluginDirectory = SettingKey[File](
    "idea-plugin-directory",
    "Default base directory of IDEA config directories for this plugin")

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

  lazy val ideaTestConfigDir = SettingKey[File](
    "idea-test-config-dir",
    "IDEA's config directory for tests")

  lazy val ideaTestSystemDir = SettingKey[File](
    "idea-test-system-dir",
    "IDEA's system directory for tests")

  lazy val cleanUpTestEnvironment = TaskKey[Unit](
    "cleanup-test-environment",
    "Clean up IDEA test system and config directories")

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

  lazy val packageMappings = TaskKey[Mappings](
    "package-mappings",
    "Internal structure of plugin artifact"
  )

  lazy val packagePlugin = TaskKey[File](
    "package-plugin",
    "Create plugin distribution"
  )

  lazy val packagePluginDynamic = TaskKey[File](
    "package-plugin-dynamic",
    "Create plugin distribution extracting all classes from projects not marked as static to disk"
  )

  lazy val packagePluginZip = TaskKey[File](
    "package-plugin-zip",
    "Create plugin distribution zip file"
  )

  lazy val shadePatterns = SettingKey[Seq[ShadePattern]](
    "shade-patterns",
    "Class renaming patterns in jars"
  )

  lazy val dumpDependencyStructure = TaskKey[ProjectData](
    "dump-dependency-structure"
  )

  lazy val homePrefix: File = sys.props.get("tc.idea.prefix").map(new File(_)).getOrElse(Path.userHome)
  lazy val ivyHomeDir: File = Option(System.getProperty("sbt.ivy.home")).fold(homePrefix / ".ivy2")(file)

  sealed trait PackagingMethod

  object PackagingMethod {
    final case class Skip() extends PackagingMethod
    final case class MergeIntoParent() extends PackagingMethod
    final case class DepsOnly(targetPath: String) extends PackagingMethod
    final case class MergeIntoOther(project: Project) extends PackagingMethod
    final case class Standalone(targetPath: String = "", static: Boolean = false) extends PackagingMethod
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
    ideaPluginName      := "InsertName",
    ideaBuild           := "LATEST-EAP-SNAPSHOT",
    ideaEdition         := IdeaEdition.Community,
    ideaDownloadSources := true,
    ideaPluginDirectory   := homePrefix / s".${ideaPluginName.value}Plugin${ideaEdition.value.shortname}",
    ideaBaseDirectory     := ideaDownloadDirectory.value / ideaBuild.value,
    ideaDownloadDirectory := ideaPluginDirectory.value / "sdk",
    ideaTestConfigDir     := ideaPluginDirectory.value / "test-config",
    ideaTestSystemDir     := ideaPluginDirectory.value / "test-system",
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1), // IDEA tests can't be run in parallel
    updateIdea := Def.task { tasks.UpdateIdea.apply(
      ideaBaseDirectory.value,
      ideaEdition.value,
      ideaBuild.value,
      ideaDownloadSources.value,
      ideaExternalPlugins.all(ScopeFilter(inAnyProject)).value.flatten,
      streams.value
    )}.value,
    cleanUpTestEnvironment := {
      IO.delete(ideaTestSystemDir.value)
      IO.delete(ideaTestConfigDir.value)
    },
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

    packageOutputDir := target.value / "plugin" / ideaPluginName.value,
    ideaPluginFile   := target.value / s"${ideaPluginName.value}-${version.value}.zip",
    ideaPublishSettings := PublishSettings("", "", "", None),
    publishPlugin := tasks.PublishPlugin.apply(ideaPublishSettings.value, ideaPluginFile.value, streams.value),
    packageMethod := PackagingMethod.MergeIntoParent(),
    packageLibraryMappings := "org.scala-lang"         % "scala-.*" % ".*" -> None ::
                              "org.scala-lang.modules" % "scala-.*" % ".*" -> None :: Nil,
    packageFileMappings := Seq.empty,
    packageAdditionalProjects := Seq.empty,
    packageAssembleLibraries := false,
    dumpDependencyStructure := Def.task {
      ProjectData(
        thisProjectRef.value,
        managedClasspath.in(Compile).value,
        libraryDependencies.in(Compile).value,
        packageAdditionalProjects.value,
        packageAssembleLibraries.value,
        products.in(Compile).value,
        update.value,
        packageLibraryMappings.value,
        packageFileMappings.value,
        packageMethod.value,
        shadePatterns.value)
    }.value,
    packageMappings := Def.taskDyn {
      streams.value.log.info("started dumping structure")
      val rootProject = thisProjectRef.value
      val buildDeps = buildDependencies.value
      val data = dumpDependencyStructure.all(ScopeFilter(inAnyProject)).value.filter(_ != null)
      val outputDir = packageOutputDir.value
      val stream = streams.value
      Def.task { new StructureBuilder(stream).artifactMappings(rootProject, outputDir, data, buildDeps) }
    }.value,
    packagePlugin := Def.taskDyn {
      val outputDir = packageOutputDir.value
      val mappings  = packageMappings.value
      val stream    = streams.value
      val myTarget  = target.value
      Def.task { new DistBuilder(stream, myTarget).packageArtifact(mappings); outputDir }
    }.value,
    packagePluginDynamic := Def.taskDyn {
      val outputDir = packageOutputDir.value
      val mappings  = packageMappings.value
      val stream    = streams.value
      val myTarget  = target.value
      Def.task { new DynamicDistBuilder(stream, myTarget, outputDir).packageArtifact(mappings); outputDir }
    }.value,
    packagePluginZip := Def.task {
      val outputDir = packagePlugin.value.getParentFile
      val pluginFile = ideaPluginFile.value
      implicit val stream: TaskStreams = streams.value
      IO.delete(pluginFile)
      new ZipDistBuilder(pluginFile).produceArtifact(outputDir)
      pluginFile
    }.value,
    aggregate.in(packagePluginZip) := false,
    aggregate.in(packageMappings) := false,
    aggregate.in(packagePlugin) := false,
    aggregate.in(updateIdea) := false,
    unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",

    shadePatterns := Seq.empty,

    // Test-related settings

    fork in Test := true,
    parallelExecution := false,
    logBuffered := false,
    javaOptions in Test := Seq(
      "-Xms128m",
      "-Xmx4096m",
      "-server",
      "-ea",
      s"-Didea.system.path=${ideaTestSystemDir.value}",
      s"-Didea.config.path=${ideaTestConfigDir.value}",
      s"-Dsbt.ivy.home=$ivyHomeDir",
      s"-Dplugin.path=${packageOutputDir.value}"
    ),
    envVars in Test += "NO_FS_ROOTS_ACCESS_CHECK" -> "yes"
  )

}
