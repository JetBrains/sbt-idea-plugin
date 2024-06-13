package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download.plugin.PluginDescriptor
import org.jetbrains.sbtidea.productInfo.{ProductInfo, ProductInfoExtraDataProvider}
import sbt.Keys.*
import sbt.{Init as _, *}

object Keys extends Defns with Init with Utils with Quirks {

  lazy val intellijPluginName = settingKey[String](
    "Name of the plugin you're developing")

  lazy val intellijBuild = settingKey[String](
    "Number of IntelliJ IntelliJ Platform build to use in project")

  lazy val intellijPlatform = settingKey[IntelliJPlatform](
    "Edition of Intellij Platform to use in project")

  private[sbtidea] lazy val intellijBuildInfo = settingKey[download.BuildInfo]("")

  lazy val jbrInfo = settingKey[JbrInfo](
    "Version and variant of JetBrains Runtime to download and install")

  lazy val intellijDownloadDirectory = settingKey[File](
    "Directory where IntelliJ Platform binaries and sources are downloaded")

  lazy val intellijPlugins = settingKey[Seq[IntellijPlugin]](
    "List of IntelliJ platform plugin to depend on")

  lazy val intellijExtraRuntimePluginsInTests = settingKey[Seq[IntellijPlugin]](
    "List of IntelliJ platform plugins to include in tests at runtime")

  lazy val intellijDownloadSources = settingKey[Boolean](
    "Flag indicating whether IntelliJ Platform sources should be downloaded too")

  lazy val intellijAttachSources = settingKey[Boolean](
    "Flag indicating whether to add sources to IntelliJ Platform SDK libraries")


  lazy val searchPluginId = inputKey[Map[String, (String, Boolean)]](
    "Search for plugin ID by plugin name or description")

  lazy val updateIntellij = taskKey[Unit](
    "Download Intellij IntelliJ Platform binaries, sources and external plugins for specified build")

  lazy val publishPlugin =inputKey[Unit](
    "Publish IntelliJ Platform plugin on plugins.jetbrains.com")

  lazy val signPlugin = taskKey[File](
    "Sign the zipped plugin artifact using your private key and certificate chain.")

  lazy val signPluginOptions = settingKey[PluginSigningOptions](
    "Enable/Disable plugin signing and set the private key and certificate chain via this setting.")

  lazy val intellijPluginDirectory = settingKey[File](
    "Default base directory of IntelliJ Platform config directories for this plugin")

  lazy val intellijBaseDirectory = settingKey[File](
    "Directory where downloaded IntelliJ Platform binaries and sources are unpacked")


  lazy val productInfo = taskKey[ProductInfo](
    "Information about IntelliJ distribution extracted from product-info.json file from IntelliJ Platform root directory")

  lazy val intellijMainJars = taskKey[Seq[File]](
    "Classpath containing main IntelliJ Platform jars")
  lazy val intellijTestJars = taskKey[Seq[File]](
    "Classpath containing IntelliJ Platform test framework jars")

  lazy val intellijPluginJars = taskKey[Seq[PluginJars]](
    "Classpath containing jars of internal IntelliJ Platform plugins used in this project")
  lazy val intellijExtraRuntimePluginsJarsInTests = taskKey[Seq[PluginJars]](
    "Classpath containing jars of extra plugins added to test classpath at runtime")

  private[sbtidea] lazy val intellijMainJarsClasspath = taskKey[Classpath](
    "Attributed classpath containing main IntelliJ Platform jars")
  private[sbtidea] lazy val intellijTestJarsClasspath = taskKey[Classpath](
    "Attributed classpath containing IntelliJ Platform test framework jars")
  private[sbtidea] lazy val intellijPluginJarsClasspath = taskKey[Seq[(PluginDescriptor, Classpath)]](
    "Attributed classpath containing jars of internal IntelliJ Platform plugins used in this project")
  private[sbtidea] lazy val intellijExtraRuntimePluginsJarsInTestsClasspath = taskKey[Seq[(PluginDescriptor, Classpath)]](
    "Attributed classpath containing jars of extra plugins added to test classpath at runtime")

  lazy val intellijTestConfigDir = settingKey[File](
    "IntelliJ Platform's config directory for tests")

  lazy val intellijTestSystemDir = settingKey[File](
    "IntelliJ Platform's system directory for tests")

  lazy val cleanUpTestEnvironment = taskKey[Unit](
    "Clean up IntelliJ Platform test system and config directories")

  lazy val patchPluginXml = settingKey[pluginXmlOptions](
    "Settings for patching plugin.xml")

  lazy val intellijVMOptions = settingKey[IntellijVMOptions](
    "Custom IntelliJ Platform JVM options used for running. The final list of all VM options is defined by IntellijVMOptionsBuilder")

  //TODO: create alias, proper descriptipon
  lazy val productInfoExtraDataProvider = taskKey[ProductInfoExtraDataProvider]("TODO")

  lazy val runIDE = inputKey[Unit](
    "Runs debug IntelliJ Platform instance with plugin")

  lazy val ideaConfigOptions = settingKey[IdeaConfigBuildingOptions](
    "Options to tune generation of IDEA run configurations")

  lazy val bundleScalaLibrary = settingKey[Boolean](
    "Include scala-library.jar in the artifact and generated run configurations")

  lazy val instrumentThreadingAnnotations = settingKey[Boolean](
    "Generate JVM bytecode to assert that a method is called on the correct IDEA thread " ++
      "(supported method annotations: @RequiresBackgroundThread, @RequiresEdt, @RequiresReadLock, @RequiresReadLockAbsence, @RequiresWriteLock)")

  /* Deprecated task aliases */

  lazy val buildIntellijOptionsIndex = taskKey[Unit](
    "Build index for searching plugin options")

  lazy val runPluginVerifier = taskKey[File](
    "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IntelliJ IDE builds")

  lazy val pluginVerifierOptions = settingKey[PluginVerifierOptions](
    "Options for the IntelliJ Plugin Verifier tool")

  /* Utility tasks */

  lazy val doPatchPluginXml           = taskKey[Unit]("").withRank(sbt.KeyRanks.Invisible)
  lazy val doProjectSetup             = taskKey[Unit]("").withRank(sbt.KeyRanks.Invisible)
  lazy val createIDEARunConfiguration = taskKey[Unit]("").withRank(sbt.KeyRanks.Invisible)
  lazy val createIDEAArtifactXml      = taskKey[Unit]("").withRank(sbt.KeyRanks.Invisible)
}
