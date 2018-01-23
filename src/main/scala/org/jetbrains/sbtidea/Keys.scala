package org.jetbrains.sbtidea

import sbt.Keys._
import sbt._

object Keys {
  lazy val ideaBuild = SettingKey[String](
    "ideaBuild",
    "Number of Intellij IDEA build to use in project")

  lazy val ideaDownloadDirectory = SettingKey[File](
    "ideaDownloadDirectory",
    "Directory where IDEA binaries and sources are downloaded")

  lazy val ideaInternalPlugins = SettingKey[Seq[String]](
    "ideaInternalPlugins",
    "List of names of bundled Intellij IDEA plugins this project depends on")

  lazy val ideaExternalPlugins = SettingKey[Seq[IdeaPlugin]](
    "ideaExternalPlugins",
    "List of third-party plugins this project depends on")

  lazy val ideaEdition = SettingKey[IdeaEdition](
    "ideaEdition",
    "Edition of Intellij IDEA to use in project")

  lazy val ideaDownloadSources = SettingKey[Boolean](
    "ideaDownloadSources",
    "Flag indicating whether IDEA sources should be downloaded too")

  lazy val ideaPublishSettings = SettingKey[PublishSettings](
    "ideaPublishSettings",
    "Settings necessary for publishing IDEA plugin to plugins.jetbrains.com")

  lazy val ideaPluginFile = TaskKey[File](
    "ideaPluginFile",
    "IDEA plugin's file to publish to plugins.jetbrains.com")

  lazy val ideaUpdate = TaskKey[Unit](
    "ideaUpdate",
    "Download Intellij IDEA binaries, sources and external plugins for specified build")

  lazy val ideaPublishPlugin = TaskKey[String](
    "ideaPublishPlugin",
    "Publish IDEA plugin on plugins.jetbrains.com")


  lazy val ideaBaseDirectory = TaskKey[File](
    "ideaBaseDirectory",
    "Directory where downloaded IDEA binaries and sources are unpacked")

  lazy val ideaMainJars = TaskKey[Classpath](
    "ideaMainJars",
    "Classpath containing main IDEA jars")

  lazy val ideaInternalPluginsJars = TaskKey[Classpath](
    "ideaInternalPluginsJars",
    "Classpath containing jars of internal IDEA plugins used in this project")

  lazy val ideaExternalPluginsJars = TaskKey[Classpath](
    "ideaExternalPluginsJars",
    "Classpath containing jars of external IDEA plugins used in this project")

  lazy val ideaFullJars = TaskKey[Classpath](
    "ideaFullJars",
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
    ideaDownloadDirectory := Path.userHome / ".IdeaData" / "sdk",
    ideaEdition := IdeaEdition.Community,
    ideaDownloadSources := true,
    ideaBaseDirectory := ideaDownloadDirectory.value / ideaBuild.value // TODO should not be bound to ideaBuild. use unique path based on url instead?
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

    ideaUpdate := tasks.UpdateIdea.apply(
      ideaBaseDirectory.value,
      ideaEdition.value,
      ideaBuild.value,
      ideaDownloadSources.value,
      ideaExternalPlugins.value,
      streams.value
    ),

    ideaPluginFile := packageBin.in(Compile).value,
    ideaPublishSettings := PublishSettings("", "", "", None),
    ideaPublishPlugin := tasks.PublishPlugin.apply(ideaPublishSettings.value, ideaPluginFile.value, streams.value)
  )
}
