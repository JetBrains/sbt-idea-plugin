package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.tasks.IdeaConfigBuilder
import org.jetbrains.sbtidea.packaging._
import org.jetbrains.sbtidea.packaging.artifact.ExcludeFilter.ExcludeFilter
import org.jetbrains.sbtidea.packaging.artifact._
import org.jetbrains.sbtidea.structure.ProjectNode
import org.jetbrains.sbtidea.packaging.mappings.LinearMappingsBuilder
import org.jetbrains.sbtidea.structure.render.StructurePrinter
import org.jetbrains.sbtidea.structure.sbtImpl.SbtProjectStructureExtractor
import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers
import sbt.jetbrains.ideaPlugin.apiAdapter._

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

  lazy val pathExcludeFilter = SettingKey[ExcludeFilter](
    "path-exclude-filter",
    "paths to exclude within merged jars"
  )

  lazy val dumpDependencyStructure = TaskKey[SbtProjectData](
    "dump-dependency-structure"
  )

  lazy val printProjectGraph = taskKey[Unit]("Show ASCII project dependency graph in console")

  lazy val createCompilationTimeStamp: TaskKey[Unit] = taskKey("")
  lazy val createIDEARunConfiguration: TaskKey[File] = taskKey("")
  lazy val createIDEAArtifactXml     : TaskKey[Unit] = taskKey("")
  lazy val dumpDependencyStructureOffline: TaskKey[SbtProjectData] = taskKey("")
  lazy val packageMappingsOffline    : TaskKey[Mappings] = taskKey("")
  lazy val dumpStructure             : TaskKey[Unit] = taskKey("")
  lazy val dumpStructureTo           : InputKey[File] = inputKey("")

  private var compilationTimeStamp = -1L

  lazy val homePrefix: File = sys.props.get("tc.idea.prefix").map(new File(_)).getOrElse(Path.userHome)
  lazy val ivyHomeDir: File = Option(System.getProperty("sbt.ivy.home")).fold(homePrefix / ".ivy2")(file)

  case class ShadePattern(from: String, to: String)

  sealed trait PackagingMethod

  object PackagingMethod {
    final case class Skip() extends PackagingMethod
    final case class MergeIntoParent() extends PackagingMethod
    final case class DepsOnly(targetPath: String = "") extends PackagingMethod
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

  def createRunnerProject(from: ProjectReference, newProjectName: String): Project =
    Project(newProjectName, file(s"target/tools/$newProjectName"))
      .dependsOn(from % Provided)
      .settings(
        name := newProjectName,
        scalaVersion := scalaVersion.in(from).value,
        dumpDependencyStructure := null, // avoid cyclic dependencies on products task
        products := packagePluginDynamic.in(from).value :: Nil,
        packageMethod := org.jetbrains.sbtidea.Keys.PackagingMethod.Skip(),
        unmanagedJars in Compile := ideaMainJars.value,
        unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",
        mainClass in (Compile, run) := Some("com.intellij.idea.Main"),
        javaOptions in run := javaOptions.in(from, Test).value :+
          "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",
        createIDEARunConfiguration := {
          val configName = "IDEA"
          val data = IdeaConfigBuilder.buildRunConfigurationXML(
            configName,
            newProjectName,
            javaOptions.in(from, Test).value,
            ideaPluginDirectory.value)
          val outFile = baseDirectory.in(ThisBuild).value / ".idea" / "runConfigurations" / s"$configName.xml"
          IO.write(outFile, data.getBytes)
          outFile
        }
      ).enablePlugins(SbtIdeaPlugin)

  private val targetFileParser = DefaultParsers.fileParser(file("/"))
  lazy val globalSettings: Seq[Setting[_]] = Seq(
    dumpStructureTo in Global:= Def.inputTaskDyn {
      val path = targetFileParser.parsed
      createIDEAArtifactXml.?.all(ScopeFilter(inProjects(LocalRootProject))).value.flatten
      val fromStructure = dumpStructureTo.in(Global).?.value
      if (fromStructure.isDefined) {
        Def.inputTask {
          fromStructure.get.fullInput(path.getAbsolutePath).evaluated
        }.toTask("")
      } else
      Def.task { new File(".") }
    }.evaluated,
    dumpStructure := Def.task {
      createIDEAArtifactXml.?.all(ScopeFilter(inProjects(LocalRootProject))).value.flatten
      dumpStructure.in(Global).?.value
    }.value
  )

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
    updateIdea := {
      new tasks.download.CommunityIdeaUpdater(ideaBaseDirectory.value)(streams.value.log)
        .updateIdeaAndPlugins(
          tasks.download.BuildInfo(
            ideaBuild.value,
            ideaEdition.value
          ),
          ideaExternalPlugins.?.all(ScopeFilter(inAnyProject)).value.flatten.flatten,
          ideaDownloadSources.value
        )
    },
    cleanUpTestEnvironment := {
      IO.delete(ideaTestSystemDir.value)
      IO.delete(ideaTestConfigDir.value)
    },
    createCompilationTimeStamp := Def.task { compilationTimeStamp = System.currentTimeMillis() }.value,
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
    printProjectGraph := {
      val rootProject = thisProjectRef.value
      val buildDeps = buildDependencies.value
      val data = dumpDependencyStructureOffline.?.all(ScopeFilter(inAnyProject)).value.flatten.filterNot(_ == null)
      implicit val logger: SbtPluginLogger = new SbtPluginLogger(streams.value)
      val structure = new SbtProjectStructureExtractor(rootProject, data, buildDeps).extract
      streams.value.log.info(new StructurePrinter().renderASCII(structure.last))
    },
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
        shadePatterns.value,
        pathExcludeFilter.value
      )
    }.value,
    dumpDependencyStructureOffline := Def.task {
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
        packageMethod.value,
        shadePatterns.value,
        pathExcludeFilter.value
      )
    }.value,
    packageMappings := Def.taskDyn {
      streams.value.log.info("started dumping structure")
      val rootProject = thisProjectRef.value
      val buildDeps = buildDependencies.value
      val data = dumpDependencyStructure.?.all(ScopeFilter(inAnyProject)).value.flatten.filterNot(_ == null)
      val outputDir = packageOutputDir.value
      implicit val logger: SbtPluginLogger = new SbtPluginLogger(streams.value)
      Def.task {
        val structure = new SbtProjectStructureExtractor(rootProject, data, buildDeps).extract
        new LinearMappingsBuilder(outputDir).buildMappings(structure)
      }
    }.value,
    packageMappingsOffline := Def.taskDyn {
      streams.value.log.info("started dumping offline structure")
      val rootProject = thisProjectRef.value
      val buildDeps = buildDependencies.value
      val data = dumpDependencyStructureOffline.?.all(ScopeFilter(inAnyProject)).value.flatten.filterNot(_ == null)
      val outputDir = packageOutputDir.value
      implicit val logger: SbtPluginLogger = new SbtPluginLogger(streams.value)
      Def.task {
        val structure = new SbtProjectStructureExtractor(rootProject, data, buildDeps).extract
        new LinearMappingsBuilder(outputDir).buildMappings(structure)
      }
    }.value,
    packagePlugin := Def.taskDyn {
      val outputDir = packageOutputDir.value
      val mappings  = packageMappings.value
      val stream    = streams.value
      val myTarget  = target.value
      Def.task { new DistBuilder(stream, myTarget).produceArtifact(mappings); outputDir }
    }.value,
    packagePluginDynamic := Def.sequential(createCompilationTimeStamp, Def.task {
      val outputDir = packageOutputDir.value
      val mappings  = packageMappings.value
      val stream    = streams.value
      val myTarget  = target.value
      val hints = extractAffectedFiles(compilationTimeStamp, compile.all(ScopeFilter(inAnyProject, inConfigurations(Compile))).value)
      new DynamicDistBuilder(stream, myTarget, outputDir, hints).produceArtifact(mappings)
      outputDir
    }).value,
    packagePluginZip := Def.task {
      val outputDir = packagePlugin.value.getParentFile
      val pluginFile = ideaPluginFile.value
      implicit val stream: TaskStreams = streams.value
      IO.delete(pluginFile)
      new ZipDistBuilder(pluginFile).produceArtifact(outputDir)
      pluginFile
    }.value,
    createIDEAArtifactXml := Def.taskDyn {
      val buildRoot = baseDirectory.in(ThisBuild).value
      val projectRoot = baseDirectory.in(ThisProject).value

      if (buildRoot == projectRoot)
        Def.task {
          val outputDir = packageOutputDir.value
          val mappings  = packageMappingsOffline.value
          val projectName = thisProject.value.id
          val result = new IdeaArtifactXmlBuilder(projectName, outputDir).produceArtifact(mappings)
          val file = buildRoot / ".idea" / "artifacts" / s"$projectName.xml"
          IO.write(file, result)
        }
      else Def.task { }
    }.value,
    aggregate.in(packagePluginZip) := false,
    aggregate.in(packageMappings) := false,
    aggregate.in(packagePlugin) := false,
    aggregate.in(updateIdea) := false,
    unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",

    shadePatterns := Seq.empty,
    pathExcludeFilter := ExcludeFilter.AllPass,

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
