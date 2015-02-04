package ideaplugin

import sbt._
import sbt.Keys._

object Keys {
  lazy val ideaVersion = SettingKey[String]("idea-version",
    "Version of Intellij IDEA to build against")
  lazy val ideaBaseDirectory = SettingKey[File]("idea-base-directory",
    "Directory where downloaded IDEA is unpacked")
  lazy val ideaMainJars = SettingKey[Classpath]("idea-main-jars",
    "Classpath containing main IDEA jars")
  lazy val ideaCommunityJars = SettingKey[Classpath]("idea-community-jars",
    "Classpath containing jars of IDEA Community plugins")
  lazy val ideaFullJars = SettingKey[Classpath]("idea-full-jars",
    "Concatenation of idea-main-jars and idea-community-jars")

  lazy val updateIdea = TaskKey[Unit]("update-idea",
    "Download Intellij IDEA binaries and sources for specified version")

  lazy val ideaPluginSettings: Seq[Setting[_]] = Seq(
    ideaVersion       := "0.0",
    ideaBaseDirectory := unmanagedBase.value / "IDEA",
    ideaMainJars      := (ideaBaseDirectory.value / "lib" * "*.jar").classpath,

    ideaCommunityJars := {
      val plugins = Seq("copyright", "gradle", "Groovy", "IntelliLang",
                        "java-i18n", "android", "maven", "junit", "properties")
      val dirs = plugins.foldLeft(PathFinder.empty){ (paths, plugin) =>
        paths +++ (ideaBaseDirectory.value / "plugins" / plugin / "lib")
      }
      (dirs * (globFilter("*.jar") -- "*asm*.jar")).classpath
    },

    ideaFullJars := ideaMainJars.value ++ ideaCommunityJars.value,
    unmanagedJars in Compile ++= ideaFullJars.value,

    updateIdea := Tasks.updateIdea
  )

}
