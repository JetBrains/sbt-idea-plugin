package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download._
import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.sbtidea.packaging.artifact.IdeaArtifactXmlBuilder
import org.jetbrains.sbtidea.runIdea.{IdeaRunner, IntellijVMOptions}
import org.jetbrains.sbtidea.xml.{PluginXmlDetector, PluginXmlPatcher}
import sbt.Keys._
import sbt.complete.DefaultParsers
import sbt.{file, _}

trait Init { this: Keys.type =>

  private val targetFileParser = DefaultParsers.fileParser(file("/"))
  protected lazy val homePrefix: File = sys.props.get("tc.idea.prefix").map(new File(_)).getOrElse(Path.userHome)
  protected lazy val ivyHomeDir: File = Option(System.getProperty("sbt.ivy.home")).fold(homePrefix / ".ivy2")(file)

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
    intellijPluginName      := name.in(LocalRootProject).value,
    intellijBuild           := "LATEST-EAP-SNAPSHOT",
    intellijPlatform         := IntelliJPlatform.IdeaCommunity,
    intellijDownloadSources := true,
    intellijPluginDirectory   := homePrefix / s".${intellijPluginName.value}Plugin${intellijPlatform.value.edition}",
    intellijBaseDirectory     := intellijDownloadDirectory.value / intellijBuild.value,
    intellijDownloadDirectory := intellijPluginDirectory.value / "sdk",
    intellijTestConfigDir     := intellijPluginDirectory.value / "test-config",
    intellijTestSystemDir     := intellijPluginDirectory.value / "test-system",
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1), // IDEA tests can't be run in parallel
    updateIntellij := {
      val logger = new SbtPluginLogger(streams.value)
      new CommunityIdeaUpdater(intellijBaseDirectory.value.toPath, logger)
        .updateIdeaAndPlugins(
          BuildInfo(
            intellijBuild.value,
            intellijPlatform.value
          ),
          intellijExternalPlugins.?.all(ScopeFilter(inAnyProject)).value.flatten.flatten,
          intellijDownloadSources.value
        )
    },
    cleanUpTestEnvironment := {
      IO.delete(intellijTestSystemDir.value)
      IO.delete(intellijTestConfigDir.value)
    },

    onLoad in Global := ((s: State) => {
      "updateIntellij" :: s
    }) compose (onLoad in Global).value
  )

  lazy val projectSettings: Seq[Setting[_]] = Seq(
    intellijInternalPlugins := Seq.empty,
    intellijExternalPlugins := Seq.empty,
    intellijMainJars := (intellijBaseDirectory.value / "lib" * "*.jar").classpath,
    intellijPluginJars :=
      tasks.CreatePluginsClasspath(intellijBaseDirectory.value / "plugins",
        intellijInternalPlugins.value,
        intellijExternalPlugins.value,
        new SbtPluginLogger(streams.value)),

    intellijFullJars := intellijMainJars.value ++ intellijPluginJars.value,
    unmanagedJars in Compile ++= intellijFullJars.value,

    packageOutputDir := target.value / "plugin" / intellijPluginName.value,
    packageArtifactZipFile := target.value / s"${intellijPluginName.value}-${version.value}.zip",
    patchPluginXml := pluginXmlOptions.DISABLED,
    packageArtifact := {
      implicit val logger: SbtPluginLogger = new SbtPluginLogger(streams.value)
      val options = patchPluginXml.value
      val productDirs = productDirectories.in(Compile).value
      if (options != pluginXmlOptions.DISABLED) {
        val detectedXmls = productDirs.flatMap(f => PluginXmlDetector.getPluginXml(f.toPath))
        detectedXmls.foreach { xml => new PluginXmlPatcher(xml).patch(options) }
      }
      packageArtifact.value
    },
    packageArtifactDynamic := {
      implicit val logger: SbtPluginLogger = new SbtPluginLogger(streams.value)
      val options = patchPluginXml.value
      val productDirs = productDirectories.in(Compile).value
      if (options != pluginXmlOptions.DISABLED) {
        val detectedXmls = productDirs.flatMap(f => PluginXmlDetector.getPluginXml(f.toPath))
        detectedXmls.foreach { xml => new PluginXmlPatcher(xml).patch(options) }
      }
      packageArtifactDynamic.value
    },
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

    intellijVMOptions :=
      IntellijVMOptions(intellijPlatform.value, packageOutputDir.value.toPath, intellijPluginDirectory.value.toPath),

    runIDE := {
      import complete.DefaultParsers._
      implicit val log: PluginLogger = new SbtPluginLogger(streams.value)
      val opts = spaceDelimited("[noPCE] [noDebug] [suspend]").parsed
      val vmOptions = intellijVMOptions.value.copy(
        noPCE = opts.contains("noPCE"),
        debug = !opts.contains("noDebug"),
        suspend = opts.contains("suspend")
      )
      val ideaCP = intellijMainJars.value.map(_.data.toPath)
      val pluginRoot = packageArtifact.value.toPath
      val runner = new IdeaRunner(ideaCP, pluginRoot, vmOptions)
      runner.run()
    },

    aggregate.in(packageArtifactZip) := false,
    aggregate.in(packageMappings) := false,
    aggregate.in(packageArtifact) := false,
    aggregate.in(updateIntellij) := false,
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

    fork in Test      := true,
    logBuffered       := false,
    parallelExecution := false,
    intellijVMOptions in Test := intellijVMOptions.value.copy(test = true, debug = false),
    javaOptions       in Test := intellijVMOptions.in(Test).value.asSeq :+ s"-Dsbt.ivy.home=$ivyHomeDir",
    envVars           in Test += "NO_FS_ROOTS_ACCESS_CHECK" -> "yes"
  )
}
