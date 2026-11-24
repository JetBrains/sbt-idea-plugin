package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download.*
import org.jetbrains.sbtidea.instrumentation.ManipulateBytecode
import org.jetbrains.sbtidea.packaging.PackagingKeys.*
import org.jetbrains.sbtidea.productInfo.{ProductInfoExtraDataProviderImpl, ProductInfoParser}
import org.jetbrains.sbtidea.runtimeCleanup.{RuntimeCleanup, RuntimeMaintenance}
import org.jetbrains.sbtidea.searchableoptions.BuildIndex
import org.jetbrains.sbtidea.tasks.*
import org.jetbrains.sbtidea.tasks.classpath.{AttributedClasspathTasks, ExternalDependencyClasspathTasks, PluginClasspathUtils, TestClasspathTasks}
import sbt.Keys.*
import sbt.{Keys as _, *}

import scala.annotation.nowarn
import scala.concurrent.duration.*

@nowarn("msg=a pure expression does nothing in statement position")
trait Init { this: Keys.type =>

  protected lazy val homePrefix: File = sys.props.get("tc.idea.prefix").map(new File(_)).getOrElse(Path.userHome)
  protected lazy val ivyHomeDir: File = Option(System.getProperty("sbt.ivy.home")).fold(homePrefix / ".ivy2")(file)
  private var updateFinished = false

  private def isRunningFromIDEA: Boolean = sys.props.contains("idea.managed")

  private lazy val defaultRuntimeDirectory = AttributeKey[Boolean]("intellij-default-runtime-directory")

  private def maintenanceLogger(logger: sbt.util.Logger): PluginLogger = new PluginLogger {
    override def debug(message: => String): Unit = logger.debug(message)
    override def info(message: => String): Unit = logger.info(message)
    override def warn(message: => String): Unit = logger.warn(message)
    override def error(message: => String): Unit = logger.error(message)
    override def fatal(message: => String): Unit = logger.error(message)
  }

  // Keep ownership on the default tasks, so an exact-path override is user-managed even
  // when its path happens to match the managed layout. Reading task metadata does not run it.
  private def defaultRuntimeDirectorySetting(setting: Def.Setting[Task[File]]): Def.Setting[Task[File]] =
    setting.mapInit { (_, task) =>
      task.copy(info = task.info.set(defaultRuntimeDirectory, true))
    }

  private def explicitRuntimeDirectory(key: TaskKey[File]): Def.Initialize[Task[Set[java.nio.file.Path]]] = Def.taskDyn {
    if (key.taskValue.info.get(defaultRuntimeDirectory).contains(true)) Def.task { Set.empty[java.nio.file.Path] }
    else Def.task { Set(key.value.toPath) }
  }

  lazy val globalSettings : Seq[Setting[?]] = Seq(
    intellijAttachSources     := true,
    // Provide defaults for settings that are aggregated across all dependencies
    // (including external projects loaded via RootProject/ProjectRef).
    // Without these defaults, `ScopeFilter(inDependencies(ThisProject))` fails
    // when a dependency project doesn't have sbt-idea-plugin enabled.
    intellijPlugins           := Seq.empty,
    intellijExtraRuntimePluginsInTests := Seq.empty
  )

  lazy val buildSettings: Seq[Setting[?]] = Seq(
    intellijPluginName        := name.in(LocalRootProject).value,
    intellijBuild             := BuildInfo.LATEST_EAP_SNAPSHOT,
    intellijPlatform          := IntelliJPlatform.Idea,
    intellijBuildInfo         := BuildInfo(intellijBuild.value, intellijPlatform.value),
    jbrInfo                   := AutoJbr(),
    intellijPluginDirectory   := homePrefix / s".${intellijPluginName.value.removeSpaces}Plugin${intellijPlatform.value.edition}",
    checkoutRuntimeDirectories := CheckoutRuntimeDirectories(intellijPluginDirectory.value.toPath, (ThisBuild / baseDirectory).value),
    intellijRuntimeBuildId := {
      val info = productInfo.value
      require(info.productCode.nonEmpty && info.buildNumber.nonEmpty,
        "The installed target SDK must report a product code and full build number in product-info.json")
      CheckoutRuntimeDirectories.validateBuildId(s"${info.productCode}-${info.buildNumber}")
    },
    defaultRuntimeDirectorySetting(
      intellijTestConfigDir := checkoutRuntimeDirectories.value.tests(intellijRuntimeBuildId.value).config.toFile),
    defaultRuntimeDirectorySetting(
      intellijTestSystemDir := checkoutRuntimeDirectories.value.tests(intellijRuntimeBuildId.value).system.toFile),
    intellijDevUseSharedDirectories := CheckoutRuntimeDirectories.sharedDevDefault,
    defaultDevRuntimeDirectories := Def.taskDyn {
      val resolver = checkoutRuntimeDirectories.value
      if (intellijDevUseSharedDirectories.value) Def.task { resolver.sharedDev }
      else Def.task { resolver.dev(intellijRuntimeBuildId.value) }
    }.value,
    defaultDevClientRuntimeDirectories := Def.taskDyn {
      val resolver = checkoutRuntimeDirectories.value
      if (intellijDevUseSharedDirectories.value) Def.task { resolver.sharedDevClient }
      else Def.task { resolver.devClient(intellijRuntimeBuildId.value) }
    }.value,
    defaultRuntimeDirectorySetting(intellijDevSystemDir := defaultDevRuntimeDirectories.value.system.toFile),
    defaultRuntimeDirectorySetting(intellijDevConfigDir := defaultDevRuntimeDirectories.value.config.toFile),
    defaultRuntimeDirectorySetting(intellijDevClientSystemDir := defaultDevClientRuntimeDirectories.value.system.toFile),
    defaultRuntimeDirectorySetting(intellijDevClientConfigDir := defaultDevClientRuntimeDirectories.value.config.toFile),
    intellijBuildIndexDirectories := checkoutRuntimeDirectories.value.buildIndex(intellijRuntimeBuildId.value),
    intellijBaseDirectory     := intellijPluginDirectory.value / "sdk" / intellijBuild.value,
    artifactsDownloadsDir     := intellijPluginDirectory.value / "sdk" / "downloads",
    autoRemoveOldCachedIntelliJSDK := false,
    autoRemoveOldCachedDownloads := false,
    autoRemoveOldCachedIntelliJRuntimes := true,
    intellijRuntimeCleanupRetention := 7.days,
    intellijRuntimeCleanupInterval := 24.hours,
    runtimeMaintenance := new RuntimeMaintenance(
      intellijPluginDirectory.value.toPath,
      checkoutRuntimeDirectories.value,
      RuntimeCleanup.Policy(autoRemoveOldCachedIntelliJRuntimes.value,
        intellijRuntimeCleanupRetention.value, intellijRuntimeCleanupInterval.value),
      Seq(intellijRuntimeBuildId.taskValue, collectIntellijRuntimeDirectoryOverrides.taskValue),
      maintenanceLogger(sLog.value),
    ),
    collectIntellijRuntimeDirectoryOverrides :=
      intellijRuntimeDirectoryOverrides.?.all(ScopeFilter(inAnyProject)).value.flatten.flatten.toSet,
    prepareIntellijRuntimeMaintenance := Def.taskDyn {
      val maintenance = runtimeMaintenance.value
      if (maintenance.overridesReady) Def.task { () }
      else Def.task {
        collectIntellijRuntimeDirectoryOverrides.result.value match {
          case Value(paths) => maintenance.initializeOverrides(paths)
          case Inc(_) => PluginLogger.debug("IntelliJ runtime cleanup deferred: explicit directory overrides are unavailable")
        }
      }
    }.value,
    showIntellijRuntimeCleanup := runtimeMaintenance.value.showCleanupReport(),
    initializeIntellijRuntimeMaintenanceOnLoad := {
      // The root may not enable the plugin or aggregate the projects that do.
      // Use a distinct build-scoped key so optional project lookup cannot recurse.
      initializeIntellijRuntimeMaintenance.?.all(ScopeFilter(inAnyProject)).value
      ()
    },
    Global / onComplete := {
      val previous = (Global / onComplete).value
      val maintenance = runtimeMaintenance.value
      () => try previous() finally maintenance.afterBatch()
    },
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
    productInfo := {
      val productInfoFile = intellijBaseDirectory.value / "product-info.json"
      ProductInfoParser.parse(productInfoFile)
    },
    productInfoExtraDataProvider := {
      val intellijRoot = intellijBaseDirectory.value
      val info = productInfo.value
      val platform = jbrInfo.value.platform
      new ProductInfoExtraDataProviderImpl(intellijRoot, info, info.launchFor(platform))
    },
    updateIntellij := {
      PluginLogger.bind(new SbtPluginLogger(streams.value))
      val plugins = {
        val pluginDeps = intellijPlugins.?.all(ScopeFilter(inAnyProject)).value.flatten.flatten
        val runtimePlugins = intellijExtraRuntimePluginsInTests.?.all(ScopeFilter(inAnyProject)).value.flatten.flatten
        (pluginDeps ++ runtimePlugins).distinct
      }
      new CommunityUpdater(
        intellijBaseDirectory.value.toPath,
        artifactsDownloadsDir.value.toPath,
        intellijBuildInfo.value,
        jbrInfo.value,
        plugins,
        autoRemoveOldIntellijSdk = autoRemoveOldCachedIntelliJSDK.value,
        autoRemoveOldDownloads = autoRemoveOldCachedDownloads.value
      ).update()
      updateFinished = true
    },
    Test / intellijTestRuntimeDirectories :=
      IntellijTestRuntimeDirectories(intellijPluginDirectory.value.toPath),

    searchPluginId := SearchPluginId.createTask.evaluated,
    onLoad in Global := ((s: State) => {
      "doProjectSetup" :: "initializeIntellijRuntimeMaintenanceOnLoad" :: s
    }) compose (onLoad in Global).value
  )

  private def intellijPluginsJars: Def.Initialize[Task[Seq[PluginJars]]] = Def.taskDyn {
    getPluginJars(intellijPlugins.value)
  }

  private def intellijExtraRuntimePluginsInTestsJars: Def.Initialize[Task[Seq[PluginJars]]] = Def.taskDyn {
    getPluginJars(intellijExtraRuntimePluginsInTests.value)
  }

  private def getPluginJars(plugins: Seq[IntellijPlugin]): Def.Initialize[Task[Seq[PluginJars]]] = Def.task {
    PluginClasspathUtils.buildPluginJars(
      intellijBaseDirectory.in(ThisBuild).value.toPath,
      intellijBuildInfo.in(ThisBuild).value,
      plugins,
      new SbtPluginLogger(streams.value),
      name.value
    )
  }

  lazy val projectSettings: Seq[Setting[?]] = Seq(
    intellijMainJars := {
      val p = productInfoExtraDataProvider.value
      p.bootClasspathJars ++ p.productModulesJars
    },
    intellijTestJars := {
      val extraDataProvider = productInfoExtraDataProvider.value
      extraDataProvider.testFrameworkJars
    },
    intellijPluginJars := intellijPluginsJars.value,
    intellijExtraRuntimePluginsJarsInTests := intellijExtraRuntimePluginsInTestsJars.value,

    intellijPlugins := Seq.empty,
    intellijExtraRuntimePluginsInTests := Seq.empty,
    intellijExtraJUnitTemplateLibraryDependencies := Seq.empty,

    ivyConfigurations += intellijJUnitTemplateConfig,
    libraryDependencies ++= intellijExtraJUnitTemplateLibraryDependencies.value
      .map(_ % intellijJUnitTemplateConfig),

    intellijMainJarsClasspath := AttributedClasspathTasks.main.value,
    intellijTestJarsClasspath := AttributedClasspathTasks.test.value,
    intellijPluginJarsClasspath := AttributedClasspathTasks.plugins.value,
    intellijExtraRuntimePluginsJarsInTestsClasspath := AttributedClasspathTasks.extraRuntimePluginsInTests.value,

    Compile / externalDependencyClasspath ++= ExternalDependencyClasspathTasks.main.value,
    Test / externalDependencyClasspath ++= ExternalDependencyClasspathTasks.test.value,

    update := UpdateWithIDEAInjectionTask.createTask.value,

    runPluginVerifier     := RunPluginVerifierTask.createTask.value,
    pluginVerifierOptions := RunPluginVerifierTask.defaultVerifierOptions.value,

    packageOutputDir := target.value / "plugin" / intellijPluginName.in(ThisBuild).value.removeSpaces,
    packageArtifactZipFile := target.value / s"${intellijPluginName.in(ThisBuild).value.removeSpaces}-${version.value}.zip",
    patchPluginXml := pluginXmlOptions.DISABLED,
    doPatchPluginXml := PatchPluginXmlTask.createTask.value,
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
    managedClasspath in Compile := {
      val previousValue: Classpath = managedClasspath.in(Compile).value
      if (bundleScalaLibrary.in(ThisBuild).value)
        previousValue
      else
        filterScalaLibraryCp(previousValue)
    },
    packageArtifact := {
      // packageMappings must complete before patching
      packageArtifact dependsOn Def.sequential(packageMappings, doPatchPluginXml)
    }.value,
    packageArtifactDynamic := {
      // packageMappings must complete before patching
      packageArtifactDynamic dependsOn Def.sequential(packageMappings, doPatchPluginXml)
    }.value,
    packageArtifactZip := Def.sequential(
      buildIntellijOptionsIndex.toTask,
      doPackageArtifactZip.toTask,
    ).value,

    publishPlugin     := PublishPlugin.createTask.evaluated,
    signPlugin        := SignPluginArtifactTask.createTask.value,
    signPluginOptions := SignPluginArtifactTask.defaultSignOptions,

    createIDEAArtifactXml := CreateIdeaArtifactXmlTask.createTask.value,
    createIDEARunConfiguration := GenerateIdeaRunConfigurations.createTask.value,
    ideaConfigOptions := IdeaConfigBuildingOptions(),

    customIntellijVMOptions := CustomIntellijVMOptions(),
    baseIntellijVMOptionsBuilder := new IntellijVMOptionsBuilder(
      platform = (ThisBuild / intellijPlatform).value,
      productInfoExtraDataProvider = (ThisBuild / productInfoExtraDataProvider).value,
      pluginPath = packageOutputDir.value.toPath,
      ideaHome = (ThisBuild / intellijPluginDirectory).value.toPath,
      intellijDirectory = (ThisBuild / intellijBaseDirectory).value.toPath,
    ),
    intellijVMOptionsBuilder := baseIntellijVMOptionsBuilder.value.withRuntimeDirectories(
      IntellijTestRuntimeDirectories.RuntimeDirectories(
        system = intellijDevSystemDir.value.toPath,
        config = intellijDevConfigDir.value.toPath,
        log = intellijDevSystemDir.value.toPath.resolve("log"),
      ),
    ),
    intellijTestVMOptionsBuilder := baseIntellijVMOptionsBuilder.value.copy(
      testRuntimeDirectories = Some(IntellijTestRuntimeDirectories.RuntimeDirectories(
        system = (Test / intellijTestSystemDir).value.toPath,
        config = (Test / intellijTestConfigDir).value.toPath,
        log = (Test / intellijTestSystemDir).value.toPath.resolve("log"),
      )),
    ),

    runIDE := RunIDETask.createTask.evaluated,

    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    unmanagedResourceDirectories in Test    += baseDirectory.value / "testResources",

    instrumentThreadingAnnotations := false,
    instrumentNotNullAnnotations := false,
    notNullAnnotations := Seq("org.jetbrains.annotations.NotNull"),
    Compile / manipulateBytecode := ManipulateBytecode.manipulateBytecodeTask(Compile).value,
    Test / manipulateBytecode := ManipulateBytecode.manipulateBytecodeTask(Test).value,

    aggregate.in(packageArtifactZip) := false,
    aggregate.in(packageMappings) := false,
    aggregate.in(packageArtifact) := false,
    aggregate.in(updateIntellij) := false,
    aggregate.in(Test) := false,
    // Deprecated task aliases
    buildIntellijOptionsIndex := BuildIndex.createTask.value,

    // Test-related settings
    intellijRuntimeDirectoryOverrides := {
      // Only explicit tasks run here. In particular, unused Dev defaults remain lazy.
      explicitRuntimeDirectory(Test / intellijTestSystemDir).value ++
        explicitRuntimeDirectory(Test / intellijTestConfigDir).value ++
        explicitRuntimeDirectory(intellijDevSystemDir).value ++
        explicitRuntimeDirectory(intellijDevConfigDir).value ++
        explicitRuntimeDirectory(intellijDevClientSystemDir).value ++
        explicitRuntimeDirectory(intellijDevClientConfigDir).value
    },
    refreshIntellijRuntimeUsage := Def.taskDyn {
      val maintenance = runtimeMaintenance.value
      val system = (Test / intellijTestSystemDir).taskValue.info.get(defaultRuntimeDirectory).contains(true)
      val config = (Test / intellijTestConfigDir).taskValue.info.get(defaultRuntimeDirectory).contains(true)
      if (!system && !config) Def.task { () }
      else Def.task {
        val resolver = checkoutRuntimeDirectories.value
        val id = intellijRuntimeBuildId.value
        val testDirs = resolver.tests(id)
        val indexDirs = resolver.buildIndex(id)
        val selectedSystem = (Test / intellijTestSystemDir).value.toPath
        val selectedConfig = (Test / intellijTestConfigDir).value.toPath
        val owned = Map("index.system" -> indexDirs.system, "index.config" -> indexDirs.config) ++
          Option("test.system" -> selectedSystem).filter(_ => system && selectedSystem == testDirs.system) ++
          Option("test.config" -> selectedConfig).filter(_ => config && selectedConfig == testDirs.config)
        maintenance.register(id, owned, RuntimeCleanup.Roles)
      }
    }.value,
    initializeIntellijRuntimeMaintenance := Def.taskDyn {
      val _ = prepareIntellijRuntimeMaintenance.value
      Def.task {
        refreshIntellijRuntimeUsage.result.value match {
          case Inc(_) =>
            runtimeMaintenance.value.invalidate()
            PluginLogger.debug("IntelliJ runtime maintenance skipped: target SDK/runtime context is unavailable")
          case _ => ()
        }
      }
    }.value,
    prepareIntellijTestDirectories := Def.taskDyn {
      val _ = initializeIntellijRuntimeMaintenance.value
      val resolver = checkoutRuntimeDirectories.value
      val system = (Test / intellijTestSystemDir).value
      val config = (Test / intellijTestConfigDir).value
      val managedSystem = Option(system).filter(_ =>
        (Test / intellijTestSystemDir).taskValue.info.get(defaultRuntimeDirectory).contains(true))
      val managedConfig = Option(config).filter(_ =>
        (Test / intellijTestConfigDir).taskValue.info.get(defaultRuntimeDirectory).contains(true))
      if (managedSystem.nonEmpty || managedConfig.nonEmpty) Def.task {
        resolver.prepareTests(intellijRuntimeBuildId.value, managedSystem, managedConfig)
      } else Def.task { () }
    }.value,
    cleanUpTestEnvironment := {
      IO.delete((Test / intellijTestSystemDir).value)
      IO.delete((Test / intellijTestConfigDir).value)
    },
    logBuffered       := false,
    parallelExecution := false,

    Test / envVars           += "NO_FS_ROOTS_ACCESS_CHECK" -> "yes",

    Test / testOnly := { (Test / testOnly).dependsOn(packageArtifact).evaluated },
    Test / test     := { (Test / test).dependsOn(packageArtifact).value },

    Test / fullClasspath := TestClasspathTasks.fullTestClasspathForSbt.value,

    // We need to fork tests to pass custom VM options
    Test / fork := true,
    Test / forkOptions := {
      val options = (Test / forkOptions).dependsOn(prepareIntellijTestDirectories).value
      RuntimeDirectoryLog.report(RuntimePurpose.Tests, name.value, options.runJVMOptions, new SbtPluginLogger(streams.value))
      options
    },

    Test / customIntellijVMOptions := customIntellijVMOptions.value.copy(debugInfo = None),

    Test / javaOptions ++= {
      val intellijVMOptionsBuilder = Keys.intellijTestVMOptionsBuilder.value

      val testVmOptions = intellijVMOptionsBuilder.build(
        vmOptions = (Test / customIntellijVMOptions).value,
        forTests = true,
        quoteValues = false
      )
      testVmOptions :+ s"-Dsbt.ivy.home=$ivyHomeDir"
    }
  )
}
