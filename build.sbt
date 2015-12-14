import bintray.Keys._
import com.typesafe.sbt.SbtGit._


lazy val commonSettings = Seq(
  name         := "sbt-idea-plugin",
  organization := "com.dancingrobot84",
  sbtPlugin    := true,
  scalacOptions ++= Seq("-deprecation", "-feature"),
  libraryDependencies +=
    "org.scalaj" %% "scalaj-http" % "2.2.0"
)


lazy val publishSettings = bintrayPublishSettings ++ Seq(
  publishMavenStyle     := false,
  repository in bintray := "sbt-plugins",
  bintrayOrganization in bintray := None,
  licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
)

commonSettings ++ publishSettings


versionWithGit

git.baseVersion := "0.0"


ScriptedPlugin.scriptedSettings

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
    Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value)
}

scriptedBufferLog := false
