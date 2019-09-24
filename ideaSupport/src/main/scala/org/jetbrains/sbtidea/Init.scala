package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download._
import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.sbtidea.packaging.artifact.IdeaArtifactXmlBuilder
import org.jetbrains.sbtidea.runIdea.{IdeaRunner, IdeaVMOptions}
import sbt.Keys._
import sbt.complete.DefaultParsers
import sbt.{file, _}

trait Init { this: Keys.type =>

  private val targetFileParser = DefaultParsers.fileParser(file("/"))
  protected lazy val homePrefix: File = sys.props.get("tc.idea.prefix").map(new File(_)).getOrElse(Path.userHome)

  lazy val globalSettings: Seq[Setting[_]] = Seq(
    dumpStructureTo in Global:= Def.inputTaskDyn {
      val path = targetFileParser.parsed
      createIDEAArtifactXml.?.all(ScopeFilter(inProjects(LocalRootProject))).value.flatten
      createIDEARunConfiguration.?.all(ScopeFilter(inProjects(LocalRootProject))).value
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
      createIDEARunConfiguration.?.all(ScopeFilter(inProjects(LocalRootProject))).value
      dumpStructure.in(Global).?.value
    }.value
  )

  lazy val buildSettings: Seq[Setting[_]] = Seq(
    ideaPluginName      := name.in(LocalRootProject).value,
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
      val logger = new SbtPluginLogger(streams.value)
      new CommunityIdeaUpdater(ideaBaseDirectory.value.toPath, logger)
        .updateIdeaAndPlugins(
          BuildInfo(
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

    onLoad in Global := ((s: State) => {
      "updateIdea" :: s
    }) compose (onLoad in Global).value
  )

  lazy val projectSettings: Seq[Setting[_]] = Seq(
    ideaInternalPlugins := Seq.empty,
    ideaExternalPlugins := Seq.empty,
    ideaMainJars := (ideaBaseDirectory.value / "lib" * "*.jar").classpath,
    ideaPluginJars :=
      tasks.CreatePluginsClasspath(ideaBaseDirectory.value / "plugins",
        ideaInternalPlugins.value,
        ideaExternalPlugins.value,
        new SbtPluginLogger(streams.value)),

    ideaFullJars := ideaMainJars.value ++ ideaPluginJars.value,
    unmanagedJars in Compile ++= ideaFullJars.value,

    packageOutputDir := target.value / "plugin" / ideaPluginName.value,
    packageArtifactZipFile := target.value / s"${ideaPluginName.value}-${version.value}.zip",
    publishPlugin := {
      import complete.DefaultParsers._
      import tasks.PublishPlugin._
      val log = new SbtPluginLogger(streams.value)
      val maybeChannel = spaceDelimited("<channel>").parsed.headOption
      val tokenFile = file(s"${sys.props.get("user.home").getOrElse(".")}/$TOKEN_FILENAME")
      val fromEnv = sys.env.get(TOKEN_KEY)
      val fromProps = sys.props.get(TOKEN_KEY)
      val token =
        if(tokenFile.exists() && tokenFile.length() > 0)
          IO.readLines(tokenFile).headOption.getOrElse("")
        else {
          fromEnv.getOrElse(
            fromProps.getOrElse(throw new IllegalStateException(
              s"Plugin repo authorisation token not set. Please either put one into $tokenFile or set $TOKEN_KEY env or prop")
            )
          )
        }
      val pluginId = LocalPluginRegistry.extractPluginMetaData(packageOutputDir.value.toPath) match {
        case Left(error) => throw new IllegalStateException(s"Can't extract plugin id from artifact: $error")
        case Right(metadata) => metadata.id
      }
        tasks.PublishPlugin.apply(token, pluginId, maybeChannel, packageArtifactZip.value, log)
    },

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

    createIDEARunConfiguration := genCreateRunConfigurationTask.value,

    ideaVMOptions := IdeaVMOptions(packageOutputDir.value.toPath, ideaPluginDirectory.value.toPath),

    runIdea := {
      import complete.DefaultParsers._
      implicit val log: PluginLogger = new SbtPluginLogger(streams.value)
      val opts = spaceDelimited("[noPCE] [noDebug] [suspend]").parsed
      val vmOptions = ideaVMOptions.value.copy(
        noPCE = opts.contains("noPCE"),
        debug = !opts.contains("noDebug"),
        suspend = opts.contains("suspend")
      )
      val ideaCP = ideaMainJars.value.map(_.data.toPath)
      val pluginRoot = packageArtifact.value.toPath
      val runner = new IdeaRunner(ideaCP, pluginRoot, vmOptions)
      runner.run()
    },

    aggregate.in(packageArtifactZip) := false,
    aggregate.in(packageMappings) := false,
    aggregate.in(packageArtifact) := false,
    aggregate.in(updateIdea) := false,
    unmanagedJars in Compile += file(System.getProperty("java.home")).getParentFile / "lib" / "tools.jar",

    // Deprecated task aliases
    packagePlugin := {
      streams.value.log.warn("this task is deprecated, please use packageArtifact")
      packageArtifact.value
    },
    packagePluginDynamic := {
      streams.value.log.warn("this task is deprecated, please use packageArtifactDynamic")
      packageArtifactDynamic.value
    },
    packagePluginZip := {
      streams.value.log.warn("this task is deprecated, please use packageArtifactZip")
      packageArtifactZip.value
    },

    // Test-related settings

    fork in Test := true,
    parallelExecution := false,
    logBuffered := false,
    javaOptions in Test := createTestVMOptions(ideaTestSystemDir.value, ideaTestConfigDir.value, packageOutputDir.value),
    envVars in Test += "NO_FS_ROOTS_ACCESS_CHECK" -> "yes"
  )
}
