package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download.*
import org.jetbrains.sbtidea.download.plugin.PluginDescriptor
import org.jetbrains.sbtidea.instrumentation.ManipulateBytecode
import org.jetbrains.sbtidea.packaging.PackagingKeys.*
import org.jetbrains.sbtidea.productInfo.{ProductInfoExtraDataProvider, ProductInfoParser}
import org.jetbrains.sbtidea.searchableoptions.BuildIndex
import org.jetbrains.sbtidea.tasks.*
import sbt.Keys.*
import sbt.{Attributed, Def, File, file, Keys as _, *}

import scala.annotation.nowarn
import scala.collection.mutable

@nowarn("msg=a pure expression does nothing in statement position")
trait Init { this: Keys.type =>

  protected lazy val homePrefix: File = sys.props.get("tc.idea.prefix").map(new File(_)).getOrElse(Path.userHome)
  protected lazy val ivyHomeDir: File = Option(System.getProperty("sbt.ivy.home")).fold(homePrefix / ".ivy2")(file)
  private var updateFinished = false

  private def isRunningFromIDEA: Boolean = sys.props.contains("idea.managed")

  lazy val globalSettings : Seq[Setting[?]] = Seq(
    intellijAttachSources     := true
  )

  lazy val buildSettings: Seq[Setting[?]] = Seq(
    intellijPluginName        := name.in(LocalRootProject).value,
    intellijBuild             := BuildInfo.LATEST_EAP_SNAPSHOT,
    intellijPlatform          := IntelliJPlatform.IdeaCommunity,
    intellijDownloadSources   := true,
    jbrInfo                   := AutoJbr(),
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
    productInfo := {
      val productInfoFile = intellijBaseDirectory.value / "product-info.json"
      ProductInfoParser.parse(productInfoFile)
    },
    updateIntellij := {
      PluginLogger.bind(new SbtPluginLogger(streams.value))
      new CommunityUpdater(
        intellijBaseDirectory.value.toPath,
        BuildInfo(
          intellijBuild.value,
          intellijPlatform.value
        ),
        jbrInfo.value,
        {
          val pluginDeps = intellijPlugins.?.all(ScopeFilter(inAnyProject)).value.flatten.flatten
          val runtimePlugins = intellijRuntimePlugins.?.all(ScopeFilter(inAnyProject)).value.flatten.flatten
          (pluginDeps ++ runtimePlugins).distinct
        },
        intellijDownloadSources.value
      ).update()
      updateFinished = true
    },
    cleanUpTestEnvironment := {
      IO.delete(intellijTestSystemDir.value)
      IO.delete(intellijTestConfigDir.value)
    },

    searchPluginId := SearchPluginId.createTask.evaluated,
    onLoad in Global := ((s: State) => {
      "doProjectSetup" :: s
    }) compose (onLoad in Global).value
  )

  private def pluginsClasspath: Def.Initialize[Task[Seq[(PluginDescriptor, Classpath)]]] = Def.taskDyn {
    getPluginsClasspath(intellijPlugins.value)
  }

  private def runtimePluginsClasspath: Def.Initialize[Task[Seq[(PluginDescriptor, Classpath)]]] = Def.taskDyn {
    getPluginsClasspath(intellijRuntimePlugins.value)
  }

  private def getPluginsClasspath(plugins: Seq[IntellijPlugin]): Def.Initialize[Task[Seq[(PluginDescriptor, Classpath)]]] = Def.task {
    tasks.CreatePluginsClasspath.buildPluginClassPaths(
      intellijBaseDirectory.in(ThisBuild).value.toPath,
      BuildInfo(
        intellijBuild.in(ThisBuild).value,
        intellijPlatform.in(ThisBuild).value
      ),
      plugins,
      new SbtPluginLogger(streams.value),
      intellijAttachSources.in(Global).value,
      name.value
    )
  }

  lazy val projectSettings: Seq[Setting[?]] = Seq(
    productInfoExtraDataProvider := {
      val intellijRoot = intellijBaseDirectory.in(ThisBuild).value
      val info = productInfo.in(ThisBuild).value
      val platform = jbrInfo.in(ThisBuild).value.platform
      new ProductInfoExtraDataProvider(intellijRoot, info, info.launchFor(platform))
    },
    intellijMainJars := {
      val argProvider = productInfoExtraDataProvider.value
      val bootstrapJars = argProvider.bootClasspathJars
      val productModulesJars = argProvider.productModulesJars
      Attributed.blankSeq(bootstrapJars ++ productModulesJars)
    },
    intellijTestJars := {
      val argProvider = productInfoExtraDataProvider.value
      Attributed.blankSeq(argProvider.testFrameworkJars)
    },
    intellijPlugins := Seq.empty,
    intellijRuntimePlugins := Seq.empty,
    intellijPluginJars := pluginsClasspath.value,

    Compile / externalDependencyClasspath ++= UpdateWithIDEAInjectionTask.buildExternalDependencyClassPath.value,
    Runtime / externalDependencyClasspath  ++= {
      val cp = (Compile / externalDependencyClasspath).value
      val runtimePlugins = runtimePluginsClasspath.value.flatMap(_._2)
      cp ++ runtimePlugins
    },
    Test / externalDependencyClasspath ++= {
      UpdateWithIDEAInjectionTask.buildTestExternalDependencyClassPath.value ++ (Runtime / externalDependencyClasspath).value
    },

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

    intellijVMOptions :=
      IntellijVMOptions(
        intellijPlatform.in(ThisBuild).value,
        packageOutputDir.value.toPath,
        intellijPluginDirectory.in(ThisBuild).value.toPath,
        intellijBaseDirectory.in(ThisBuild).value.toPath
      ),

    runIDE := RunIDETask.createTask.evaluated,

    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    unmanagedResourceDirectories in Test    += baseDirectory.value / "testResources",

    instrumentThreadingAnnotations := false,
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

    fork in Test      := true,
    logBuffered       := false,
    parallelExecution := false,
    intellijVMOptions in Test := intellijVMOptions.value.copy(test = true, debug = false),
    envVars           in Test += "NO_FS_ROOTS_ACCESS_CHECK" -> "yes",

    testOnly.in(Test) := { testOnly.in(Test).dependsOn(packageArtifact).evaluated },
    test.in(Test)     := { test.in(Test).dependsOn(packageArtifact).value },

    fullClasspath.in(Test) := {
      val fullClasspathValue = fullClasspath.in(Test).value
      val pathFinder = PathFinder.empty +++ // the new IJ plugin loading strategy in tests requires external plugins to be prepended to the classpath
        packageOutputDir.value * globFilter("*.jar") +++
        packageOutputDir.value / "lib" * globFilter("*.jar")
      val allExportedProducts = exportedProducts.all(ScopeFilter(inDependencies(ThisProject), inConfigurations(Compile))).value.flatten
      pathFinder.classpath ++ (fullClasspathValue.to[mutable.LinkedHashSet] -- allExportedProducts.toSet).toSeq // exclude classes already in the artifact
    },

    javaOptions.in(Test) ++= { intellijVMOptions.in(Test).value.asSeq() :+ s"-Dsbt.ivy.home=$ivyHomeDir" }
  )
}
