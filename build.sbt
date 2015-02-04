import bintray.Keys._


lazy val commonSettings = Seq(
  name         := "sbt-idea-plugin",
  organization := "com.dancingrobot84",
  version      := "0.0.1",
  sbtPlugin    := true,
  scalacOptions ++= Seq("-deprecation", "-feature")
)


lazy val publishSettings = bintrayPublishSettings ++ Seq(
  publishMavenStyle     := false,
  repository in bintray := "sbt-plugins",
  bintrayOrganization in bintray := None,
  licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
)

commonSettings ++ publishSettings
