package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download._
import org.jetbrains.sbtidea.download.plugin.LocalPluginRegistry
import org.jetbrains.sbtidea.download.jbr.JbrDependency
import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.sbtidea.packaging.artifact.{DistBuilder, IdeaArtifactXmlBuilder}
import org.jetbrains.sbtidea.runIdea.{IdeaRunner, IntellijVMOptions}
import org.jetbrains.sbtidea.searchableoptions.BuildIndex
import org.jetbrains.sbtidea.tasks.SearchPluginId
import org.jetbrains.sbtidea.xml.{PluginXmlDetector, PluginXmlPatcher}
import sbt.Keys._
import sbt.complete.DefaultParsers
import sbt.{file, _}

trait Init { this: Keys.type =>

  protected lazy val homePrefix: File = sys.props.get("tc.idea.prefix").map(new File(_)).getOrElse(Path.userHome)
  protected lazy val ivyHomeDir: File = Option(System.getProperty("sbt.ivy.home")).fold(homePrefix / ".ivy2")(file)
  private var updateFinished = false

  private def isRunningFromIDEA: Boolean = sys.props.contains("idea.managed")

  lazy val buildSettings: Seq[Setting[_]] = Seq(
    intellijPluginName        := name.in(LocalRootProject).value,
    intellijBuild             := "LATEST-EAP-SNAPSHOT",
    intellijPlatform          := IntelliJPlatform.IdeaCommunity,
    intellijDownloadSources   := true,
    jbrVersion                := Some(JbrDependency.VERSION_AUTO),
    intellijPluginDirectory   := homePrefix / s".${intellijPluginName.value}Plugin${intellijPlatform.value.edition}",
    intellijBaseDirectory     := intellijDownloadDirectory.value / intellijBuild.value,
    intellijDownloadDirectory := intellijPluginDirectory.value / "sdk",
    intellijTestConfigDir     := intellijPluginDirectory.value / "test-config",
    intellijTestSystemDir     := intellijPluginDirectory.value / "test-system",
    generateJUnitTemplate     := true,
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1), // IDEA tests can't be run in parallel
    doProjectSetup := Def.taskDyn {
      if (!updateFinished && isRunningFromIDEA) Def.task {
        println("Detected IDEA, artifacts and run configurations have been generated")
        updateIntellij.value
        createIDEAArtifactXml.?.all(ScopeFilter(inProjects(LocalRootProject))).value.flatten
        createIDEARunConfiguration.?.all(ScopeFilter(inAnyProject)).value
        updateFinished = true
      } else if (!updateFinished && !isRunningFromIDEA) Def.task {
        updateIntellij.value
        updateFinished = true
      } else Def.task { }
    }.value,
    updateIntellij := {
      PluginLogger.bind(new SbtPluginLogger(streams.value))
      new CommunityUpdater(
        intellijBaseDirectory.value.toPath,
        BuildInfo(
          intellijBuild.value,
          intellijPlatform.value,
          jbrVersion.value
        ),
        intellijPlugins.?.all(ScopeFilter(inAnyProject)).value.flatten.flatten,
        intellijDownloadSources.value
      ).update()
    },
    cleanUpTestEnvironment := {
      IO.delete(intellijTestSystemDir.value)
      IO.delete(intellijTestConfigDir.value)
    },
    searchPluginId := {
      import complete.DefaultParsers._
      val log = streams.value.log
      val parsed = spaceDelimited("[--nobundled|--noremote] <plugin name regexp>").parsed
      val maybeQuery = parsed.lastOption.filterNot(_.startsWith("--"))
      PluginLogger.bind(new SbtPluginLogger(streams.value))
      val result: Map[String, (String, Boolean)] = maybeQuery match {
        case Some(query) =>
          val searcher = new SearchPluginId(
            intellijBaseDirectory.value.toPath,
            BuildInfo(
              intellijBuild.value,
              intellijPlatform.value,
              jbrVersion.value
            ),
            useBundled = !parsed.contains("--nobundled"),
            useRemote  = !parsed.contains("--noremote")
          )
          searcher(query)
        case None =>
          log.error(s"search query expected")
          Map.empty
      }
      result.foreach {
        case (id, (name, false)) => log.info(s"bundled\t\t- $name[$id]")
        case (id, (name, true)) => log.info(s"from repo\t- $name[$id]")
      }
      result
    },
    onLoad in Global := ((s: State) => {
      "doProjectSetup" :: s
    }) compose (onLoad in Global).value
  )

  lazy val projectSettings: Seq[Setting[_]] = Seq(
    intellijInternalPlugins := Seq.empty,
    intellijExternalPlugins := Seq.empty,
    intellijPlugins := intellijInternalPlugins.value.map(IntellijPlugin.BundledFolder(_)) ++ intellijExternalPlugins.value,
    intellijMainJars := (intellijBaseDirectory.value / "lib" * "*.jar").classpath,
    intellijPluginJars :=
      tasks.CreatePluginsClasspath(
        intellijBaseDirectory.value.toPath,
        BuildInfo(
          intellijBuild.value,
          intellijPlatform.value,
          jbrVersion.value
        ),
        intellijPlugins.value,
        new SbtPluginLogger(streams.value),
        name.value),

    intellijFullJars := intellijMainJars.value ++ intellijPluginJars.value,
    unmanagedJars in Compile ++= intellijFullJars.value,

    packageOutputDir := target.value / "plugin" / intellijPluginName.value,
    packageArtifactZipFile := target.value / s"${intellijPluginName.value}-${version.value}.zip",
    patchPluginXml := pluginXmlOptions.DISABLED,
    doPatchPluginXml := {
      PluginLogger.bind(new SbtPluginLogger(streams.value))
      val options = patchPluginXml.value
      val productDirs = productDirectories.in(Compile).value
      if (options != pluginXmlOptions.DISABLED) {
        val detectedXmls = productDirs.flatMap(f => PluginXmlDetector.getPluginXml(f.toPath))
        PluginLogger.info(s"Detected plugin xmls, patching: ${detectedXmls.mkString("\n")}")
        detectedXmls.foreach { xml => new PluginXmlPatcher(xml).patch(options) }
      }
    },
    libraryDependencies := {
      val previousValue = libraryDependencies.value
      val plugins       = intellijPlugins.all(ScopeFilter(inAnyProject)).value.flatten
      if (hasPluginsWithScala(plugins))
        makeScalaLibraryProvided(previousValue)
      else
        previousValue
    },
    packageLibraryMappings := {
      val previousValue = packageLibraryMappings.value
      val plugins       = intellijPlugins.all(ScopeFilter(inAnyProject)).value.flatten
      if (hasPluginsWithScala(plugins))
        filterScalaLibrary(previousValue)
      else
        previousValue
    },
    packageArtifact := {
      doPatchPluginXml.value
      packageArtifact.value
    },
    packageArtifactDynamic := {
      doPatchPluginXml.value
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
        tasks.PublishPlugin.apply(token, pluginId.repr, maybeChannel, packageArtifactZip.value, log)
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
    createIDEARunConfiguration := {},

    intellijVMOptions :=
      IntellijVMOptions(intellijPlatform.value, packageOutputDir.value.toPath, intellijPluginDirectory.value.toPath),

    runIDE := {
      import complete.DefaultParsers._
      PluginLogger.bind(new SbtPluginLogger(streams.value))
      val opts = spaceDelimited("[noPCE] [noDebug] [suspend] [blocking]").parsed
      val vmOptions = intellijVMOptions.value.copy(
        noPCE = opts.contains("noPCE"),
        debug = !opts.contains("noDebug"),
        suspend = opts.contains("suspend")
      )
      val ideaCP = intellijMainJars.value.map(_.data.toPath)
      val runner = new IdeaRunner(ideaCP, vmOptions, opts.contains("blocking"))
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
    buildIntellijOptionsIndex := BuildIndex.createTask.value,

    // Test-related settings

    fork in Test      := true,
    logBuffered       := false,
    parallelExecution := false,
    intellijVMOptions in Test := intellijVMOptions.value.copy(test = true, debug = false),
    javaOptions       in Test := intellijVMOptions.in(Test).value.asSeq :+ s"-Dsbt.ivy.home=$ivyHomeDir",
    envVars           in Test += "NO_FS_ROOTS_ACCESS_CHECK" -> "yes"
  )
}
