package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download._
import org.jetbrains.sbtidea.download.idea.IdeaSourcesImpl
import org.jetbrains.sbtidea.download.jbr.JbrDependency
import org.jetbrains.sbtidea.download.plugin.{LocalPluginRegistry, PluginDescriptor}
import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.sbtidea.packaging.artifact.IdeaArtifactXmlBuilder
import org.jetbrains.sbtidea.runIdea.{IdeaRunner, IntellijVMOptions}
import org.jetbrains.sbtidea.searchableoptions.BuildIndex
import org.jetbrains.sbtidea.tasks.SearchPluginId
import org.jetbrains.sbtidea.xml.{PluginXmlDetector, PluginXmlPatcher}
import sbt.Keys._
import sbt.{file, _}

trait Init { this: Keys.type =>

  protected lazy val homePrefix: File = sys.props.get("tc.idea.prefix").map(new File(_)).getOrElse(Path.userHome)
  protected lazy val ivyHomeDir: File = Option(System.getProperty("sbt.ivy.home")).fold(homePrefix / ".ivy2")(file)
  private var updateFinished = false

  private def isRunningFromIDEA: Boolean = sys.props.contains("idea.managed")

  lazy val globalSettings : Seq[Setting[_]] = Seq(
    intellijAttachSources     := true
  )

  lazy val buildSettings: Seq[Setting[_]] = Seq(
    intellijPluginName        := name.in(LocalRootProject).value,
    intellijBuild             := "LATEST-EAP-SNAPSHOT",
    intellijPlatform          := IntelliJPlatform.IdeaCommunity,
    intellijDownloadSources   := true,
    jbrVersion                := Some(JbrDependency.VERSION_AUTO),
    intellijPluginDirectory   := homePrefix / s".${intellijPluginName.value.removeSpaces}Plugin${intellijPlatform.value.edition}",
    intellijBaseDirectory     := intellijDownloadDirectory.value / intellijBuild.value,
    intellijDownloadDirectory := intellijPluginDirectory.value / "sdk",
    intellijTestConfigDir     := intellijPluginDirectory.value / "test-config",
    intellijTestSystemDir     := intellijPluginDirectory.value / "test-system",
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1), // IDEA tests can't be run in parallel
    bundleScalaLibrary        := !hasPluginsWithScala(intellijPlugins.?.all(ScopeFilter(inAnyProject)).value.flatten.flatten),
    doProjectSetup := Def.taskDyn {
      if (!updateFinished && isRunningFromIDEA) Def.sequential(
        updateIntellij,
        Def.task {
          println("Detected IDEA, artifacts and run configurations have been generated")
          createIDEAArtifactXml.?.all(ScopeFilter(inProjects(LocalRootProject))).value.flatten
          createIDEARunConfiguration.?.all(ScopeFilter(inProjects(LocalRootProject))).value
          updateFinished = true
        }) else if (!updateFinished && !isRunningFromIDEA) Def.task {
        updateIntellij.value
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
      updateFinished = true
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

  private val ideaJarsArtifact    = Artifact("INTELLIJ-SDK", "IJ-SDK")
  private val ideaSourcesArtifact = Artifact("INTELLIJ-SDK", Artifact.SourceType, "zip", "IJ-SDK")
  private def buildIdeaModule(build: String): ModuleID =
    "org.jetbrains" % "INTELLIJ-SDK" % build withSources()

  lazy val projectSettings: Seq[Setting[_]] = Seq(
    intellijPlugins     := Seq.empty,
    intellijMainJars    := (intellijBaseDirectory.in(ThisBuild).value / "lib" * "*.jar").classpath,
    intellijPluginJars  :=
      tasks.CreatePluginsClasspath.buildPluginClassPaths(
        intellijBaseDirectory.in(ThisBuild).value.toPath,
        BuildInfo(
          intellijBuild.in(ThisBuild).value,
          intellijPlatform.in(ThisBuild).value,
          jbrVersion.in(ThisBuild).value
        ),
        intellijPlugins.value,
        new SbtPluginLogger(streams.value),
        name.value),

    externalDependencyClasspath in Compile ++= {
      val ideaModule: ModuleID = buildIdeaModule(intellijBuild.in(ThisBuild).value)
      val sdkAttrs: AttributeMap = AttributeMap.empty
        .put(artifact.key, ideaJarsArtifact)
        .put(moduleID.key, ideaModule)
        .put(configuration.key, Compile)

      val pluginClassPaths = intellijPluginJars.value.flatMap(_._2)

      intellijMainJars.value.map(_.data).map{ i=>Attributed(i)(sdkAttrs)} ++ pluginClassPaths
    },

    update := {
      import org.jetbrains.sbtidea.ApiAdapter._
      val attachSources           = intellijAttachSources.in(Global).value
      val ijBuild                 = intellijBuild.in(ThisBuild).value
      val ideaModule              = buildIdeaModule(ijBuild)
      val originalReport          = update.value
      val intelliJSourcesArchive  = intellijBaseDirectory.value / IdeaSourcesImpl.SOURCES_ZIP
      val ideaArtifacts           =
        intellijMainJars.value.map(ideaJarsArtifact -> _.data) ++
          (if (attachSources)  Seq(ideaSourcesArtifact -> intelliJSourcesArchive)
           else                Seq.empty)
      val reportWithIdeaMainJars  = injectIntoUpdateReport(originalReport, ideaArtifacts, ideaModule)

      val pluginClassPaths = intellijPluginJars.value
      pluginClassPaths.foldLeft(reportWithIdeaMainJars) { case (report, (_, classpath)) =>
        classpath.headOption.map { f =>
          val pluginModule = f.get(moduleID.key).get
          val pluginArtifact = f.get(artifact.key).get
          if (attachSources && pluginModule.revision == ijBuild) { // bundled plugin has the same version as platform, add sources
            val pluginArtifacts = classpath.map(pluginArtifact -> _.data) :+
              (Artifact(pluginArtifact.name, Artifact.SourceType, "zip", Artifact.SourceClassifier) -> intelliJSourcesArchive)
            injectIntoUpdateReport(report, pluginArtifacts, pluginModule)
          } else {
            val pluginArtifacts = classpath.map(pluginArtifact -> _.data)
            injectIntoUpdateReport(report, pluginArtifacts, pluginModule)
          }
        }.getOrElse(report)
      }
    },

    packageOutputDir := target.value / "plugin" / intellijPluginName.in(ThisBuild).value.removeSpaces,
    packageArtifactZipFile := target.value / s"${intellijPluginName.in(ThisBuild).value.removeSpaces}-${version.value}.zip",
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
      if (bundleScalaLibrary.value)
        previousValue
      else
        makeScalaLibraryProvided(previousValue)
    },
    packageLibraryMappings := {
      val previousValue = packageLibraryMappings.value
      if (bundleScalaLibrary.in(ThisBuild).value)
        previousValue
      else
        filterScalaLibrary(previousValue)
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

    createIDEARunConfiguration := genCreateRunConfigurationTask.value,
    ideaConfigOptions := IdeaConfigBuildingOptions(),

    intellijVMOptions :=
      IntellijVMOptions(intellijPlatform.in(ThisBuild).value, packageOutputDir.value.toPath, intellijPluginDirectory.in(ThisBuild).value.toPath),

    runIDE := {
      import complete.DefaultParsers._
      packageArtifact.value // build the plugin before running
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

    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    unmanagedResourceDirectories in Test    += baseDirectory.value / "testResources",

    aggregate.in(packageArtifactZip) := false,
    aggregate.in(packageMappings) := false,
    aggregate.in(packageArtifact) := false,
    aggregate.in(updateIntellij) := false,
    aggregate.in(Test) := false,
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
    envVars           in Test += "NO_FS_ROOTS_ACCESS_CHECK" -> "yes",

    testOnly.in(Test) := { testOnly.in(Test).dependsOn(packageArtifact).evaluated },

    fullClasspath.in(Test) := {
      val oldClasspath = fullClasspath.in(Test).value
      if (VersionComparatorUtil.compare(intellijBuild.value, newClassloadingSinceVersion) >= 0) {
        val pathFinder = PathFinder.empty +++ // the new IJ plugin loading strategy in tests requires external plugins to be prepended to the classpath
          (packageOutputDir.value * globFilter("*.jar")) +++
          (packageOutputDir.value / "lib" * globFilter("*.jar"))
        val allExportedProducts = exportedProducts.all(ScopeFilter(inDependencies(ThisProject), inConfigurations(Compile))).value.flatten
        pathFinder.classpath ++ (oldClasspath.toSet -- allExportedProducts.toSet).toSeq // exclude classes already in the artifact
      } else oldClasspath
    },

    javaOptions.in(Test) ++= { intellijVMOptions.in(Test).value.asSeq :+ s"-Dsbt.ivy.home=$ivyHomeDir" }
  )
}
