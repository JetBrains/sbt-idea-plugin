package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.runIdea.IdeaVMOptions
import sbt.Keys._
import sbt._

object Keys extends Defns with Init with Utils with Quirks {

  lazy val ideaPluginName = settingKey[String](
    "Name of the plugin you're developing")

  lazy val ideaBuild = settingKey[String](
    "Number of IntelliJ IDEA build to use in project")

  lazy val ideaDownloadDirectory = settingKey[File](
    "Directory where IDEA binaries and sources are downloaded")

  lazy val ideaInternalPlugins = settingKey[Seq[String]](
    "List of names of bundled IntelliJ IDEA plugins this project depends on")

  lazy val ideaExternalPlugins = settingKey[Seq[IdeaPlugin]](
    "List of third-party plugins this project depends on")

  lazy val ideaEdition = settingKey[IdeaEdition](
    "Edition of Intellij IDEA to use in project")

  lazy val ideaDownloadSources = settingKey[Boolean](
    "Flag indicating whether IDEA sources should be downloaded too")

  lazy val updateIdea = taskKey[Unit](
    "Download Intellij IDEA binaries, sources and external plugins for specified build")

  lazy val publishPlugin =inputKey[Unit](
    "Publish IDEA plugin on plugins.jetbrains.com")

  lazy val ideaPluginDirectory = settingKey[File](
    "Default base directory of IDEA config directories for this plugin")

  lazy val ideaBaseDirectory = settingKey[File](
    "Directory where downloaded IDEA binaries and sources are unpacked")

  lazy val ideaMainJars = taskKey[Classpath](
    "Classpath containing main IDEA jars")

  lazy val ideaPluginJars = taskKey[Classpath](
    "Classpath containing jars of internal IDEA plugins used in this project")

  lazy val ideaFullJars = taskKey[Classpath](
    "Complete classpath of IDEA's and internal and external plugins' jars")

  lazy val ideaTestConfigDir = settingKey[File](
    "IDEA's config directory for tests")

  lazy val ideaTestSystemDir = settingKey[File](
    "IDEA's system directory for tests")

  lazy val cleanUpTestEnvironment = taskKey[Unit](
    "Clean up IDEA test system and config directories")

  lazy val ideaVMOptions = settingKey[IdeaVMOptions](
    "IDEA platform java VM options used for running"
  )

  lazy val runIdea = inputKey[Unit](
    "Runs debug IDEA instance with plugin"
  )

  /* Deprecated task aliases */

  lazy val packagePlugin = taskKey[File](
    "Produce the plugin artifact")

  lazy val packagePluginDynamic = taskKey[File](
    "Create plugin distribution extracting all classes from projects not marked as static to disk")

  lazy val packagePluginZip = taskKey[File](
    "Create plugin distribution zip file")

  /* Utility tasks */

  lazy val createIDEARunConfiguration: TaskKey[File] = taskKey("")
  lazy val createIDEAArtifactXml     : TaskKey[Unit] = taskKey("")

  lazy val dumpStructure             : TaskKey[Unit] = taskKey("")
  lazy val dumpStructureTo           : InputKey[File] = inputKey("")

}
