package org.jetbrains.sbtidea

import sbt.Keys._
import sbt._

object Keys {
  lazy val ideaBuild = SettingKey[String](
    "idea-build",
    "Number of Intellij IDEA build to use in project")

  lazy val ideaDownloadDirectory = SettingKey[File](
    "idea-download-directory",
    "Directory where IDEA binaries and sources are downloaded")

  lazy val ideaInternalPlugins = SettingKey[Seq[String]](
    "idea-internal-plugins",
    "List of names of bundled Intellij IDEA plugins this project depends on")

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


  sealed trait IdeaPlugin {
    val name: String
    val url: URL
  }

  object IdeaPlugin {
    final case class Zip(name: String, url: URL) extends IdeaPlugin
    final case class Jar(name: String, url: URL) extends IdeaPlugin
  }

  sealed trait IdeaEdition {
    val name: String
  }

  object IdeaEdition {
    object Community extends IdeaEdition {
      override val name = "ideaIC"
    }

    object Ultimate extends IdeaEdition {
      override val name = "ideaIU"
    }
  }

  final case class PublishSettings(pluginId: String, username: String, password: String, channel: Option[String])


  lazy val buildSettings: Seq[Setting[_]] = Seq(
    ideaBuild := "LATEST-EAP-SNAPSHOT",
    ideaDownloadDirectory := baseDirectory.value / "idea",
    ideaEdition := IdeaEdition.Community,
    ideaDownloadSources := true,
    ideaBaseDirectory := ideaDownloadDirectory.value / ideaBuild.value
  )

  lazy val projectSettings: Seq[Setting[_]] = Seq(
    ideaInternalPlugins := Seq.empty,
    ideaExternalPlugins := Seq.empty,
    ideaMainJars := (ideaBaseDirectory.value / "lib" * "*.jar").classpath,
    ideaInternalPluginsJars :=
      tasks.CreatePluginsClasspath(ideaBaseDirectory.value / "plugins", ideaInternalPlugins.value),

    ideaExternalPluginsJars :=
      tasks.CreatePluginsClasspath(ideaBaseDirectory.value / "externalPlugins", ideaExternalPlugins.value.map(_.name)),

    ideaFullJars := ideaMainJars.value ++ ideaInternalPluginsJars.value ++ ideaExternalPluginsJars.value,
    unmanagedJars in Compile ++= ideaFullJars.value,

    updateIdea := tasks.UpdateIdea.apply(
      ideaBaseDirectory.value,
      ideaEdition.value,
      ideaBuild.value,
      ideaDownloadSources.value,
      ideaExternalPlugins.value,
      streams.value
    ),

    ideaPluginFile := packageBin.in(Compile).value,
    ideaPublishSettings := PublishSettings("", "", "", None),
    publishPlugin := tasks.PublishPlugin.apply(ideaPublishSettings.value, ideaPluginFile.value, streams.value)
  )
}
