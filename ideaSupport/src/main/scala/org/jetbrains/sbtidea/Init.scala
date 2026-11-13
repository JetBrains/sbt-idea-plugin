package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download.*
import org.jetbrains.sbtidea.instrumentation.ManipulateBytecode
import org.jetbrains.sbtidea.packaging.PackagingKeys.*
import org.jetbrains.sbtidea.productInfo.{ProductInfoExtraDataProviderImpl, ProductInfoParser}
import org.jetbrains.sbtidea.runIdea.IntellijVMOptionsBuilder.VmOptions
import org.jetbrains.sbtidea.searchableoptions.BuildIndex
import org.jetbrains.sbtidea.tasks.*
import org.jetbrains.sbtidea.tasks.classpath.{AttributedClasspathTasks, ExternalDependencyClasspathTasks, PluginClasspathUtils, TestClasspathTasks}
import sbt.Keys.*
import sbt.{Keys as _, *}

import scala.annotation.nowarn

@nowarn("msg=a pure expression does nothing in statement position")
trait Init { this: Keys.type =>

  protected lazy val homePrefix: File = sys.props.get("tc.idea.prefix").map(new File(_)).getOrElse(Path.userHome)
  protected lazy val ivyHomeDir: File = Option(System.getProperty("sbt.ivy.home")).fold(homePrefix / ".ivy2")(file)
  private var updateFinished = false

  private def isRunningFromIDEA: Boolean = sys.props.contains("idea.managed")

  private lazy val managedTestDirectory = AttributeKey[Boolean]("intellij-managed-test-directory")

  // Keep ownership on the default tasks, so an exact-path override is user-managed even
  // when its path happens to match the managed layout. Reading task metadata does not run it.
  private def managedTestDirectorySetting(setting: Def.Setting[Task[File]]): Def.Setting[Task[File]] =
    setting.mapInit { (_, task) =>
      task.copy(info = task.info.set(managedTestDirectory, true))
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
    managedTestDirectorySetting(
      intellijTestConfigDir := checkoutRuntimeDirectories.value.tests(intellijRuntimeBuildId.value).config.toFile),
    managedTestDirectorySetting(
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
    intellijDevSystemDir := defaultDevRuntimeDirectories.value.system.toFile,
    intellijDevConfigDir := defaultDevRuntimeDirectories.value.config.toFile,
    intellijDevClientSystemDir := defaultDevClientRuntimeDirectories.value.system.toFile,
    intellijDevClientConfigDir := defaultDevClientRuntimeDirectories.value.config.toFile,
    intellijBuildIndexDirectories := checkoutRuntimeDirectories.value.buildIndex(intellijRuntimeBuildId.value),
    intellijBaseDirectory     := intellijPluginDirectory.value / "sdk" / intellijBuild.value,
    artifactsDownloadsDir     := intellijPluginDirectory.value / "sdk" / "downloads",
    autoRemoveOldCachedIntelliJSDK := false,
    autoRemoveOldCachedDownloads := false,
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
      "doProjectSetup" :: s
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

    useNewVmOptions := true,
    customIntellijVMOptions := CustomIntellijVMOptions(),
    intellijVMOptions := IntellijVMOptions(
      intellijPlatform.in(ThisBuild).value,
      packageOutputDir.value.toPath,
      intellijPluginDirectory.in(ThisBuild).value.toPath,
      intellijBaseDirectory.in(ThisBuild).value.toPath
    ): @nowarn("cat=deprecation"),
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
    prepareIntellijTestDirectories := Def.taskDyn {
      val resolver = checkoutRuntimeDirectories.value
      val system = (Test / intellijTestSystemDir).value
      val config = (Test / intellijTestConfigDir).value
      val managedSystem = Option(system).filter(_ =>
        (Test / intellijTestSystemDir).taskValue.info.get(managedTestDirectory).contains(true))
      val managedConfig = Option(config).filter(_ =>
        (Test / intellijTestConfigDir).taskValue.info.get(managedTestDirectory).contains(true))
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
    Test / forkOptions := (Test / forkOptions).dependsOn(prepareIntellijTestDirectories).value,

    Test / intellijVMOptions := intellijVMOptions.value.copy(debug = false): @nowarn("cat=deprecation"),
    Test / customIntellijVMOptions := customIntellijVMOptions.value.copy(debugInfo = None),

    Test / javaOptions ++= {
      val intellijVMOptionsBuilder = Keys.intellijTestVMOptionsBuilder.value

      val vmOptions: VmOptions = if ((Test / useNewVmOptions).value)
        VmOptions.New((Test / customIntellijVMOptions).value)
      else
        VmOptions.Old((Test / intellijVMOptions).value): @nowarn("cat=deprecation")

      val testVmOptions = intellijVMOptionsBuilder.build(
        vmOptions = vmOptions,
        forTests = true,
        quoteValues = false
      )
      testVmOptions :+ s"-Dsbt.ivy.home=$ivyHomeDir"
    }
  )
}
