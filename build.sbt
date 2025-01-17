import sbt.librarymanagement.Configurations.CompileInternal
import xerial.sbt.Sonatype.GitHubHosting

Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 1))

// Some tests test global properties and fail when tests are run in parallel
Test / parallelExecution := false

val MinimumSbtVersion = "1.4.5"
// This version should be backward compatible with MinimumSbtVersion
val SbtVersionForTests = "1.10.7"

lazy val commonSettings: Seq[Setting[?]] = Seq(
  organization := "org.jetbrains",
  sonatypeProfileName := "org.jetbrains",
  homepage := Some(url("https://github.com/JetBrains/sbt-idea-plugin")),
  sonatypeProjectHosting := Some(GitHubHosting("JetBrains", "sbt-idea-plugin", "scala-developers@jetbrains.com")),
  licenses ++= Seq(
    ("MIT", url("https://opensource.org/licenses/MIT")),
    ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0"))
  ),

  scalaVersion := "2.12.18",
  pluginCrossBuild / sbtVersion := MinimumSbtVersion,

  Compile / scalacOptions ++= Seq("-deprecation", "-feature", "-Xfatal-warnings"),

  // It's fine to require later JDK level.
  // You still JDK >= 17 when developing IntelliJ plugin as IntelliJ requires JDK 17 (at least in 2024.3)
  Compile / javacOptions ++= Seq("--release", "11"),
  Compile / scalacOptions ++= Seq("-release", "11"),

  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
  
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    // Use latest version of sbt in tests to test against latest versions of projects using this plugin
    "org.scala-sbt" % "sbt" % SbtVersionForTests % Test,
  )
)

lazy val core = (project in file("core"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .settings(
    name := "sbt-declarative-core"
  )

lazy val visualizer = (project in file("visualizer"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings(
    name := "sbt-declarative-visualizer",
    libraryDependencies += "com.github.mutcianm" %% "ascii-graphs" % "0.0.6"
  )

val circeVersion = "0.14.10"

lazy val packaging = (project in file("packaging"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings(
    name := "sbt-declarative-packaging",
    libraryDependencies ++= Seq(
      "org.pantsbuild" % "jarjar" % "1.7.2",
      "io.circe" %% "circe-core" % circeVersion % Test,
      "io.circe" %% "circe-generic" % circeVersion % Test,
      "io.circe" %% "circe-parser" % circeVersion % Test
    )
  )

lazy val ideaSupport = (project in file("ideaSupport"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .dependsOn(core, packaging, visualizer)
  .settings(
    name := "sbt-idea-plugin",
    libraryDependencies ++= Seq(
      "org.apache.httpcomponents.client5" % "httpclient5" % "5.3.1",

      "org.jetbrains" % "marketplace-zip-signer" % "0.1.24",
      "io.spray" %% "spray-json" % "1.3.6",
      "org.rauschig" % "jarchivelib" % "1.2.0",
      "org.ow2.asm" % "asm" % "9.6",
      "io.get-coursier" %% "coursier" % "2.1.10",

      //for file utils in tests (create/delete cerucsively/write string)
      "commons-io" % "commons-io" % "2.15.1" % Test
    )
  )

lazy val sbtIdeaPlugin = (project in file("."))
  .settings(commonSettings)
  .settings(publish / skip := true)
  .aggregate(core, packaging, ideaSupport, visualizer)