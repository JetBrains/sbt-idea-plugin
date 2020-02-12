package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.runIdea.IntellijVMOptions
import sbt.Keys._
import sbt._

object Keys extends Defns with Init with Utils with Quirks {

  lazy val intellijPluginName = settingKey[String](
    "Name of the plugin you're developing")

  lazy val intellijBuild = settingKey[String](
    "Number of IntelliJ IntelliJ Platform build to use in project")

  lazy val intellijDownloadDirectory = settingKey[File](
    "Directory where IntelliJ Platform binaries and sources are downloaded")

  lazy val intellijInternalPlugins = settingKey[Seq[String]](
    "List of names of bundled IntelliJ IntelliJ Platform plugins this project depends on")

  lazy val intellijExternalPlugins = settingKey[Seq[IntellijPlugin]](
    "List of third-party plugins this project depends on")

  lazy val intellijPlatform = settingKey[IntelliJPlatform](
    "Edition of Intellij Platform to use in project")

  lazy val intellijDownloadSources = settingKey[Boolean](
    "Flag indicating whether IntelliJ Platform sources should be downloaded too")

  lazy val jbrVersion = settingKey[Option[String]](
    "Version of JetBrains Runtime to download and install"
  )

  lazy val updateIntellij = taskKey[Unit](
    "Download Intellij IntelliJ Platform binaries, sources and external plugins for specified build")

  lazy val publishPlugin =inputKey[Unit](
    "Publish IntelliJ Platform plugin on plugins.jetbrains.com")

  lazy val intellijPluginDirectory = settingKey[File](
    "Default base directory of IntelliJ Platform config directories for this plugin")

  lazy val intellijBaseDirectory = settingKey[File](
    "Directory where downloaded IntelliJ Platform binaries and sources are unpacked")

  lazy val intellijMainJars = taskKey[Classpath](
    "Classpath containing main IntelliJ Platform jars")

  lazy val intellijPluginJars = taskKey[Classpath](
    "Classpath containing jars of internal IntelliJ Platform plugins used in this project")

  lazy val intellijFullJars = taskKey[Classpath](
    "Complete classpath of IntelliJ Platform's internal and external plugins' jars")

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

  /* Deprecated task aliases */

  lazy val packagePlugin = taskKey[File](
    "Produce the plugin artifact")

  lazy val packagePluginDynamic = taskKey[File](
    "Create plugin distribution extracting all classes from projects not marked as static to disk")

  lazy val packagePluginZip = taskKey[File](
    "Create plugin distribution zip file")

  lazy val buildIntellijOptionsIndex = taskKey[Unit](
    "Build index for searching plugin options"
  )

  /* Utility tasks */

  lazy val createIDEARunConfiguration: TaskKey[File] = taskKey("")
  lazy val createIDEAArtifactXml     : TaskKey[Unit] = taskKey("")

  lazy val dumpStructure             : TaskKey[Unit] = taskKey("")
  lazy val dumpStructureTo           : InputKey[File] = inputKey("")

}
