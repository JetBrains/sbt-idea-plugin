package ideaplugin

import sbt._
import sbt.Keys._

object Keys {
  lazy val ideaBuild = SettingKey[String](
    "idea-build",
    "Number of Intellij IDEA build to use in project")
  lazy val ideaBaseDirectory = SettingKey[File](
    "idea-base-directory",
    "Directory where downloaded IDEA is unpacked")
  lazy val ideaInternalPlugins = SettingKey[Seq[String]](
    "idea-internal-plugins",
    "List of names of bundled Intellij IDEA plugins this project depends on")
  lazy val ideaExternalPlugins = SettingKey[Seq[(String,URI)]](
    "idea-external-plugins",
    "List of (name, URI) pairs of third-party plugins this project depends on")

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

  lazy val ideaPluginSettings: Seq[Setting[_]] = Seq(
    ideaBaseDirectory := baseDirectory.value / "idea",

    ideaInternalPlugins := Seq.empty,

    ideaMainJars := (ideaBaseDirectory.value / ideaBuild.value / "lib" * "*.jar").classpath,

    ideaInternalPluginsJars <<= (ideaBaseDirectory, ideaBuild, ideaInternalPlugins).map {
      (baseDir, ideaBuild, pluginsUsed) =>
        val pluginsBase = baseDir / ideaBuild / "plugins"
        Tasks.createPluginsClasspath(pluginsBase, pluginsUsed)
    },

    ideaExternalPluginsJars <<= (ideaBaseDirectory, ideaBuild, ideaExternalPlugins).map {
      (baseDir, ideaBuild, pluginsUsed) =>
        val pluginsBase = baseDir / ideaBuild / "externalPlugins"
        Tasks.createPluginsClasspath(pluginsBase, pluginsUsed.map(_._1))
    },

    ideaFullJars := ideaMainJars.value ++ ideaInternalPluginsJars.value ++ ideaExternalPluginsJars.value,

    unmanagedJars in Compile ++= ideaFullJars.value,

    updateIdea <<= (ideaBaseDirectory, ideaBuild, streams).map(Tasks.updateIdea)
  )
}
