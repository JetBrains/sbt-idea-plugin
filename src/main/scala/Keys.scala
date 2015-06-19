package ideaplugin

import sbt._
import sbt.Keys._

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

  lazy val updateIdea = TaskKey[Unit](
    "update-idea",
    "Download Intellij IDEA binaries, sources and external plugins for specified build")


  sealed trait IdeaPlugin {
    val name: String
    val url: URL
  }

  object IdeaPlugin {
    final case class Zip(name: String, url: URL) extends IdeaPlugin
    final case class Jar(name: String, url: URL) extends IdeaPlugin
  }


  lazy val ideaPluginSettings: Seq[Setting[_]] = Seq(
    ideaDownloadDirectory := baseDirectory.value / "idea",

    ideaInternalPlugins := Seq.empty,

    ideaExternalPlugins := Seq.empty,

    ideaBaseDirectory <<= (ideaDownloadDirectory, ideaBuild).map {
      (downloadDir, build) => downloadDir / build
    },

    ideaMainJars := (ideaBaseDirectory.value / "lib" * "*.jar").classpath,

    ideaInternalPluginsJars <<= (ideaBaseDirectory, ideaInternalPlugins).map {
      (baseDir, pluginsUsed) => Tasks.createPluginsClasspath(baseDir / "plugins", pluginsUsed)
    },

    ideaExternalPluginsJars <<= (ideaBaseDirectory, ideaExternalPlugins).map {
      (baseDir, pluginsUsed) => Tasks.createPluginsClasspath(baseDir / "externalPlugins", pluginsUsed.map(_.name))
    },

    ideaFullJars := ideaMainJars.value ++ ideaInternalPluginsJars.value ++ ideaExternalPluginsJars.value,

    unmanagedJars in Compile ++= ideaFullJars.value,

    updateIdea <<= (ideaBaseDirectory, ideaBuild, ideaExternalPlugins, streams).map(Tasks.updateIdea)
  )
}
