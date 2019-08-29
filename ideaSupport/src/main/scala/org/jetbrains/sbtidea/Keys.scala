package org.jetbrains.sbtidea

import sbt.Keys._
import sbt._

object Keys extends Defns with Init with Utils {

  lazy val ideaPluginName = SettingKey[String](
    "idea-plugin-name",
    "Name of the plugin you're developing")

  lazy val ideaBuild = SettingKey[String](
    "idea-build",
    "Number of IntelliJ IDEA build to use in project")

  lazy val ideaDownloadDirectory = SettingKey[File](
    "idea-download-directory",
    "Directory where IDEA binaries and sources are downloaded")

  lazy val ideaInternalPlugins = SettingKey[Seq[String]](
    "idea-internal-plugins",
    "List of names of bundled IntelliJ IDEA plugins this project depends on")

  lazy val ideaExternalPlugins = SettingKey[Seq[IdeaPlugin]](
    "idea-external-plugins",
    "List of third-party plugins this project depends on")

  lazy val ideaEdition = SettingKey[IdeaEdition](
    "idea-edition",
    "Edition of Intellij IDEA to use in project")

  lazy val ideaDownloadSources = SettingKey[Boolean](
    "idea-download-sources",
    "Flag indicating whether IDEA sources should be downloaded too")

  lazy val ideaPublishSettings = SettingKey[PublishSettings](
    "idea-publish-settings",
    "Settings necessary for publishing IDEA plugin to plugins.jetbrains.com")

  lazy val ideaPluginFile = TaskKey[File](
    "idea-plugin-file",
    "IDEA plugin's file to publish to plugins.jetbrains.com")

  lazy val updateIdea = TaskKey[Unit](
    "update-idea",
    "Download Intellij IDEA binaries, sources and external plugins for specified build")

  lazy val publishPlugin = TaskKey[String](
    "publish-plugin",
    "Publish IDEA plugin on plugins.jetbrains.com")

  lazy val ideaPluginDirectory = SettingKey[File](
    "idea-plugin-directory",
    "Default base directory of IDEA config directories for this plugin")

  lazy val ideaBaseDirectory = TaskKey[File](
    "idea-base-directory",
    "Directory where downloaded IDEA binaries and sources are unpacked")

  lazy val ideaMainJars = TaskKey[Classpath](
    "idea-main-jars",
    "Classpath containing main IDEA jars")

  lazy val ideaInternalPluginsJars = TaskKey[Classpath](
    "idea-internal-plugins-jars",
    "Classpath containing jars of internal IDEA plugins used in this project")

  lazy val ideaExternalPluginsJars = TaskKey[Classpath](
    "idea-external-plugins-jars",
    "Classpath containing jars of external IDEA plugins used in this project")

  lazy val ideaFullJars = TaskKey[Classpath](
    "idea-full-jars",
    "Complete classpath of IDEA's and internal and external plugins' jars")

  lazy val ideaTestConfigDir = SettingKey[File](
    "idea-test-config-dir",
    "IDEA's config directory for tests")

  lazy val ideaTestSystemDir = SettingKey[File](
    "idea-test-system-dir",
    "IDEA's system directory for tests")

  lazy val cleanUpTestEnvironment = TaskKey[Unit](
    "cleanup-test-environment",
    "Clean up IDEA test system and config directories")

  lazy val createIDEARunConfiguration: TaskKey[File] = taskKey("")
  lazy val createIDEAArtifactXml     : TaskKey[Unit] = taskKey("")

  lazy val dumpStructure             : TaskKey[Unit] = taskKey("")
  lazy val dumpStructureTo           : InputKey[File] = inputKey("")

}
