package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download.plugin.PluginDescriptor
import sbt.Keys._
import sbt.{Init => _, _}
import sbt.jetbrains.ideaPlugin.apiAdapter.{SbtTaskKeyExt, SbtInputKeyExt}

object Keys extends Defns with Init with Utils with Quirks {

  lazy val intellijPluginName = settingKey[String](
    "Name of the plugin you're developing")

  lazy val intellijBuild = settingKey[String](
    "Number of IntelliJ IntelliJ Platform build to use in project")

  lazy val intellijDownloadDirectory = settingKey[File](
    "Directory where IntelliJ Platform binaries and sources are downloaded")

  lazy val intellijPlugins = settingKey[Seq[IntellijPlugin]](
    "List of IntelliJ platform plugin to depend on")

  lazy val intellijPlatform = settingKey[IntelliJPlatform](
    "Edition of Intellij Platform to use in project")

  lazy val intellijDownloadSources = settingKey[Boolean](
    "Flag indicating whether IntelliJ Platform sources should be downloaded too")

  lazy val intellijAttachSources = settingKey[Boolean](
    "Flag indicating whether to add sources to IntelliJ Platform SDK libraries")

  @deprecated("use jbrInfo instead", "3.9.1")
  lazy val jbrVersion = settingKey[Option[String]](
    "DEPRECATED: use jbrInfo instead")

  lazy val jbrInfo = settingKey[JbrInfo](
    "Version and variant of JetBrains Runtime to download and install")

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

  lazy val intellijMainJars = taskKey[Classpath](
    "Classpath containing main IntelliJ Platform jars")

  lazy val intellijPluginJars = taskKey[Seq[(PluginDescriptor, Classpath)]](
    "Classpath containing jars of internal IntelliJ Platform plugins used in this project")

  /**
    * Deprecated. Use intellijMainJars or intellijPluginJars
    */
  @deprecated("IJ sdk jars and plugin jars are treated separately", "4.9.0")
  lazy val intellijFullJars = taskKey[Classpath](
    "[DEPRECATED]Complete classpath of IntelliJ Platform's internal and external plugins' jars")

  lazy val intellijTestConfigDir = settingKey[File](
    "IntelliJ Platform's config directory for tests")

  lazy val intellijTestSystemDir = settingKey[File](
    "IntelliJ Platform's system directory for tests")

  lazy val cleanUpTestEnvironment = taskKey[Unit](
    "Clean up IntelliJ Platform test system and config directories")

  lazy val patchPluginXml = settingKey[pluginXmlOptions](
    "Settings for patching plugin.xml")

  lazy val intellijVMOptions = settingKey[IntellijVMOptions](
    "IntelliJ Platform java VM options used for running")

  lazy val runIDE = inputKey[Unit](
    "Runs debug IntelliJ Platform instance with plugin")

  lazy val ideaConfigOptions = settingKey[IdeaConfigBuildingOptions](
    "Options to tune generation of IDEA run configurations")

  lazy val bundleScalaLibrary = settingKey[Boolean](
    "Include scala-library.jar in the artifact and generated run configurations")

  /* Deprecated task aliases */

  lazy val buildIntellijOptionsIndex = taskKey[Unit](
    "Build index for searching plugin options")

  lazy val runPluginVerifier = taskKey[File](
    "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IntelliJ IDE builds")

  lazy val pluginVerifierOptions = settingKey[PluginVerifierOptions](
    "Options for the IntelliJ Plugin Verifier tool")

  /* Utility tasks */

  lazy val doPatchPluginXml           = taskKey[Unit]("").invisible
  lazy val doProjectSetup             = taskKey[Unit]("").invisible
  lazy val createIDEARunConfiguration = taskKey[Unit]("").invisible
  lazy val createIDEAArtifactXml      = taskKey[Unit]("").invisible
  lazy val dumpStructure              = taskKey[Unit]("").invisible
  lazy val dumpStructureTo            = inputKey[File]("").invisible

}
